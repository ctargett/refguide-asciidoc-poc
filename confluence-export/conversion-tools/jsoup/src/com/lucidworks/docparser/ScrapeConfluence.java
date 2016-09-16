package com.lucidworks.docparser;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

/**  
 * Extract body of Confluence page using Jsoup library.
 * This creates an identical (flat) directory structured containing cleaned up documents
 */
public class ScrapeConfluence {
  static final Pattern PRE_CODE_CLASS_PATTERN = Pattern.compile("brush:\\s+([^;]+)");
  static final Pattern ANCHOR_ID_CLEANER = Pattern.compile("[^A-Za-z0-9\\.\\-\\_\\#]+");
  
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: ScrapeConfluence "
                               + "<indir> <page-tree.xml> <outdir>");
            System.exit(-1);
        }
        File inputDir = new File(args[0]);
        File pageTreeXmlFile = new File(args[1]);
        PageTree pageTree = new PageTree(pageTreeXmlFile);
        File outputDir = new File(args[2]);
        File imagesDir = new File(outputDir, "images");
        if (! (imagesDir.exists() || imagesDir.mkdirs() ) ) {
          throw new RuntimeException("Unable to create images dir: " + imagesDir.toString());
        }
        
        HtmlFileFilter htmlFilter = new HtmlFileFilter();
        File[] pages = inputDir.listFiles(htmlFilter);
        for (File page : pages) {
            if (page.getName().equals("index.html")) {
              // we don't need/want you
              // although i really wish i'd realized this page was i nthe HTML export before
              // i did all that work to build page-tree.xml from the XML export
              continue;
            }
          
            System.out.println("input Page URI: " + page.toURI().toString());
            final Element pageTreePage = pageTree.getPage(page.toURI().toString());
            final String pageName = pageTree.getPageShortName(pageTreePage);
            final String title = pageTree.getPageTitle(pageTreePage);
            final String permalink = pageName + ".html";
            final File outPage = new File(outputDir, permalink);
            System.out.println("outPage URI: " + outPage.toURI().toString());
            
            if (outPage.exists()) {
              throw new RuntimeException(permalink + " already exists - multiple files with same shortname: " + page + " => " + outPage);
            }

            // Confluence encodes &nbsp; as 0xa0.
            // JSoup API doesn't handle this - change to space before parsing Document
            String fileContents = readFile(page.getPath());
            fileContents = fileContents.replace('\u00a0',' ');

            // parse Confluence page
            Document doc = Jsoup.parse(fileContents);
            Element mainContent = doc.select("#main-content").first();
            if (mainContent == null) {
              throw new RuntimeException(page.getName() + " has no main-content div");
            }
            
            // create clean HTML page
            Document docOut = Document.createShell(outPage.toURI().toString());
            docOut.title(title);

            addMetadata(docOut, "page-shortname", pageName);
            addMetadata(docOut, "page-permalink", permalink);
            for (Element kid : pageTreePage.children()) {
              addMetadata(docOut, "page-children", pageTree.getPageShortName(kid));
            }

            
            docOut.body().appendChild(mainContent);
            docOut.normalise();

            cleanupContent(docOut);

            // fix links
            Elements elements = docOut.select("a[href]");
            for (Element element : elements) {
              element.attr("href", fixLink(page, pageTree, element.attr("href")));
            }
            
            // fix (and copy) images
            for (Element element : docOut.select("img")) {
              String src = element.attr("src");
              // attachments can be referenced by other pages
              String imagePageId = element.attr("data-linked-resource-container-id");
              String filename = element.attr("data-linked-resource-default-alias");
              if (null == imagePageId || null == filename ||
                  "".equals(imagePageId) || "".equals(filename)) {
                // this some standard comfluence image, not an attacment
                // assume it's already been copied into place, and leave the src attr alone
                continue;
              }
              String imagePageShortName = pageTree.getPageShortName(pageTree.getPage
                                                                    (Integer.valueOf(imagePageId)));
              
              // copy the file to the desired path if we haven't already...
              File imagePageDir = new File(imagesDir, imagePageShortName);
              File imageFile = new File(imagePageDir, filename);
              if (! imageFile.exists()) {
                File origImageFile = new File(inputDir, src);
                if (! origImageFile.exists()) {
                  throw new RuntimeException("unable to find image: " + origImageFile + " for img in " +
                                             page.toString());
                }
                if (! (imagePageDir.exists() || imagePageDir.mkdirs() ) ) {
                  throw new RuntimeException("unable to makedirs: " + imagePageDir + " for img: " + src +
                                             " in " + page.toString());
                }
                Files.copy(origImageFile.toPath(), imageFile.toPath());
              }
              
              // rewrite the src attribute
              element.attr("src", "images/" + imagePageShortName + "/" + filename);
            }

            // TODO: need to look for non image attachments and copy them as well
            // ie: SVG files used to create some of these images
            
            docOut.normalise();
            OutputStream out = new FileOutputStream(outPage);
            Writer writer = new OutputStreamWriter(out,"UTF-8");
            BufferedWriter bufWriter = new BufferedWriter(writer);
            bufWriter.write(docOut.toString());
            bufWriter.write("\n");
            bufWriter.close();
            writer.close();
            out.close();
        }
    }

    static String readFile(String fileName) throws IOException {
        InputStream in = new FileInputStream(fileName);
        Reader reader = new InputStreamReader(in,"UTF-8");
        BufferedReader br = new BufferedReader(reader);
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

  static String fixLink(File page, PageTree pageTree, final String href) {
    try {
      URI uri = new URI(href);
      if (uri.isAbsolute()) {
        // TODO: look for lucene/solr javadoc URLs and replace them with some macro?
        return href;
      }
      // else: not an absoulte URL...
      
      // any relative URL will get 'REL_LINK//' prepended so we can post-process
      // the .adoc files to convert from the "link:xxx" syntax to the <<xxx>> syntax
      // since pandoc doesn't have native support for that.
      final String PRE = "REL_LINK//";
      
      String path = uri.getPath(); 
      Element linkedPage = pageTree.getPageIfMatch(path);
      
      if ("".equals(path)) { // fragment only URL (ie: same page)
        return PRE + fixAnchorId(href);
      } else if (null != linkedPage) {
        path = pageTree.getPageShortName(linkedPage) + ".adoc";

        String frag = uri.getFragment();
        if (null == frag) {
          // we have to have a fragment for intra-page links to work correctly in asciidoc
          frag = "";
        }
        frag = fixAnchorId(frag);
        
        // HACKish, to ensure we get clean path + ?query? + fragement
        // (assuming we have any query parts in our realtive urls to worry about)
        String fixed = new URI(null, null, path, uri.getQuery(), frag).toString();
        return PRE + fixed;
        
      } // else: no idea what this is...

      System.err.println("found odd rel link: " + href + " in " + page.toString());
      return PRE + href;

      
    } catch (URISyntaxException se) {
      System.err.println("found malformed URI " + href + " in " + page.toString());
      // assume we should leave it alone...
      return href;
    }

  }
  
  static void addMetadata(Document docOut, String name, String content) {
      Element meta = new Element(Tag.valueOf("meta"),".");
      meta.attr("name", name);
      meta.attr("content", content);
      docOut.head().appendChild(meta);
  }
  
  
  static void cleanupContent(Document docOut) {
    // start cleanup
    Elements elements = null;
    
    // remove side panels (page-internal ToCs)
    Element sideBar = docOut.select("[data-type=aside]").first();
    if (null == sideBar) {
      // sometimes they aren't an 'aside', they are columns cotaining panels
      elements = docOut.select("div.columnMacro");
      for (Element element : elements) {
        if (! element.select("div.toc-macro").isEmpty()) {
          sideBar = element;
          break;
        }
      }
    }
    if (null == sideBar) {
      // final scnereo: toc by itself in the page body...
      elements = docOut.select("div.toc-macro");
      for (Element element : elements) {
        if (! element.select("div.toc-macro").isEmpty()) {
          sideBar = element;
          break;
        }
      }
    }
    if (sideBar != null) {
      // TODO: this currently replaces the entire aside/column/panel if there was one...
      // ...would it be better to leave the other panel text and only remove the div.toc-macro?
      //  examples:
      //    Covered in this section:
      //    Topics covered in this section:
      //    Filters discussed in this section:
      //    Algorithms discussed in this section:

      // NOTE: conciously choosing to completely remove the TOC, instead of adding any metadata/macros to it
      // let the page presentation decide if/when to use a TOC...
      //
      sideBar.remove();
      // sideBar.replaceWith(new TextNode("toc::[]",""));
      // addMetadata(docOut, "toc", "true");
      

    } else {
      // sanity check if we missed any (multiple TOCs on a page?) ...
      elements = docOut.select("div.toc-macro");
      if (! elements.isEmpty()) {
        System.out.println("MISSED A TOC: " + elements.toString());
        System.exit(-1);
      }
    }
    
    // unwrap various formatting tags if they are empty
    for (String tag : Arrays.asList("strong", "em", "p", "code", "pre")) {
      elements = docOut.getElementsByTag(tag);
      for (Element element : elements) {
        if (!element.hasText()) {
          element.unwrap(); // unwrap not remove! (even w/o text might be inner nodes, ex: img)
        }
      }
    }

    // remove confluence styles
    elements = docOut.select("[style]");
    for (Element element : elements) {
      element.removeAttr("style");
    }
    // remove confluence themes from <pre> tags
    elements = docOut.getElementsByTag("pre");
    for (Element element : elements) {
      if (element.hasAttr("class")) {
        Matcher codeType = PRE_CODE_CLASS_PATTERN.matcher(element.attr("class"));
        if (codeType.find()) {
          String codeClass = codeType.group(1);
          // some munging needed in some cases...
          if (codeClass.equals("html/xml")) {
            codeClass = "xml";
          }
          if (codeClass.equals("js")) {
            // almost no javascript in ref guide, assume it should really be json
            codeClass = "json";
          }
          if (element.text().startsWith("curl ")) {
            // if this looks like a curl command, then ignore whatever class might have been in
            // confluence and treat it as bash
            codeClass = "bash";
          }
          // TODO: other values we should also change here? "powershell" ?
          element.attr("class", codeClass);
        } else {
          element.removeAttr("class");
        }
      }
    }

    // confluence has a nasty habbit of (sometimes) putting named anchors people explicitly define
    // *inside* a header, instead of around/before it.
    // so if we find any of these, we need to rearange some things to work around somr problems...
    // https://github.com/asciidoctor/asciidoctor/issues/1875
    // 
    // NOTE: just moving an explicit anchor before the header should work, but because of how id's on headers
    // are treated in asciidoc, and some weirdness in how asciidoctor treats multiple anchors
    // delcared in front of a header this causes all but one of the anchors to be ignored...
    //
    // https://github.com/asciidoctor/asciidoctor/issues/1874
    //
    // because of this, we'll use the "explicitly" defined ancor macro from confluence as our "main"
    // id for the header, and move the existing header id to it's own declaration.
    //
    // that should result in both still existing in the final adoc file (so they are easy to grep for)
    // but the one that is most likely to have links to it will be the one used by default in generated html.
    for (int level = 1; level < 7; level++) {
      final String h = "h" + level;
      elements = docOut.getElementsByTag(h);
      for (Element header : elements) {
        // first see if we are immediately preceeded by an explicit anchor macro...
        // (any wrapping <p> tags should have already been uprapped for us)
        Element previous = header.previousElementSibling();
        if (null != previous && "span".equals(previous.tagName()) && previous.classNames().contains("confluence-anchor-link")) {
          // swap the id from this "previous" macro declaration with the "id" of the as our header
          final String oldId = header.attr("id");
          header.attr("id", previous.attr("id"));
          previous.attr("id", oldId);
        }
          
        // next, look for any anchors declared inside the header...
        Elements inner = header.getElementsByClass("confluence-anchor-link");
        for (Element anchor : inner) {
          final String oldId = header.attr("id");
          header.attr("id", anchor.attr("id"));
          if (null != oldId) {
            // flip id and move the anchor before the header
            anchor.attr("id", oldId);
            header.before(anchor);
          } else {
            // just remove the anchor completley
            // (don't think this code path is possible, but including for completeness)
            anchor.remove();
          }
        }
      }
    }
    
    // replace icon text
    elements = docOut.getElementsByClass("confluence-information-macro");
    for (Element element : elements) {
      final String admonishment = getAdmonishment(element);
      Elements titles = element.select(".title");
      if (1 < titles.size()) {
        System.err.println("admonishment macro has more then 1 title: " + element.outerHtml());
        System.exit(-1);
      }

      // it's easier to post-process this, then to try and fight the html->pandoc->adoc conversion
      for (Element title : titles) { // only one, loop is easy
        title.prependText("TODO_ADMON_TITLE:");
        element.before(title); // move it before the block
      }
      element.prependChild((new Element(Tag.valueOf("p"), ".")).prependText("[" + admonishment + "]===="));
      element.appendChild((new Element(Tag.valueOf("p"), ".")).prependText("===="));
    }

    // unwrap various block tags if they are empty
    for (String tag : Arrays.asList("div","tbody")) {
      elements = docOut.getElementsByTag(tag);
      for (Element element : elements) {
        element.unwrap(); // unwrap not remove! (might be inner nodes, ex: img)
      }
    }
    
    // remove breaks -- TODO: why?
    elements = docOut.getElementsByTag("br");
    for (Element element : elements) {
      element.remove();
    }

    // work around https://github.com/asciidoctor/asciidoctor/issues/1873
    elements = docOut.select("[id]");
    for (Element element : elements) {
      final String oldId = element.attr("id");
      final String newId = fixAnchorId(oldId);
      if (! oldId.equals(newId)) {
        // would love to use jsoup's Comment class, but it doesn't survive pandoc
        // ironically, this does...
        Element fakeComment = new Element(Tag.valueOf("div"), "");
        fakeComment.text("// OLD_CONFLUENCE_ID: " + oldId);
        element.before(fakeComment);
        element.attr("id", newId);
      }
    }
    
    docOut.normalise();
  }

  /** 
   * work around https://github.com/asciidoctor/asciidoctor/issues/1873
   * needs to be called on all "id" attributes, as well as any anchor text in (local) links
   */
  public static String fixAnchorId(String id) {
    Matcher m = ANCHOR_ID_CLEANER.matcher(id);
    return m.replaceAll("_");
  }

  /**
   * convert confluence admonishment macor types to the "equivilent" adoc types we want to use
   */
  public static String getAdmonishment(Element e) {
    String admon = null;
    if (e.hasClass("confluence-information-macro-information")) {
      return "NOTE";
    }
    if (e.hasClass("confluence-information-macro-tip")) {
      return "TIP";
    }
    if (e.hasClass("confluence-information-macro-note")) {
      return "IMPORTANT";
    }
    if (e.hasClass("confluence-information-macro-warning")) {
      return "WARNING";
    }
    System.err.println("No admonishment mapping for: " + e.outerHtml());
    System.exit(-1);
    return null;
  }
  
  /**
   * Wraps a (Jsoup) "DOM" of the <code>page-tree.xml</code> file with convinience methods
   * for getting the names, shortnames, and kids of various pages
   */
  private static final class PageTree {
    private static final Pattern HTML_EXPORT_FILENAME = Pattern.compile("^.*?\\D?(\\d+)\\.html$");
    private static final Pattern SHORT_NAME_CLEANER = Pattern.compile("[^a-z0-9]+");
    // Jsoups XML parsing is easier to work with then javax, especially getById
    private final Document dom;
    public PageTree(File pageTreeXml) throws Exception {
      try (FileInputStream fis = new FileInputStream(pageTreeXml)) {
        this.dom = Jsoup.parse(fis, null, pageTreeXml.toURI().toString(), Parser.xmlParser());
      }
    }
    public Element getPage(int id) {
      final Element ele = dom.getElementById(""+id);
      if (null == ele) {
        throw new NullPointerException("can't find DOM element with id: " + id);
      }
      return ele;
    }
    public Element getPage(String htmlFilePath) {
      Element page = getPageIfMatch(htmlFilePath);
      if (null != page) {
        return page;
      } // else...
      throw new RuntimeException("Can't match page path pattern for html path: " + htmlFilePath);
    }
    public Element getPageIfMatch(String htmlFilePath) {
      if (null == htmlFilePath || 0 == htmlFilePath.length()) {
        return null;
      }
      Matcher m = HTML_EXPORT_FILENAME.matcher(htmlFilePath);
      if (m.matches()) {
        int id = Integer.valueOf(m.group(1));
        return getPage(id);
      } // else...
      return null;
    }
    public String getPageTitle(Element page) {
      String title = page.attr("title");
      if (null == title) {
        throw new NullPointerException("Page has null title attr");
      }
      return title;
    }
    public String getPageShortName(Element page) {
      Matcher m = SHORT_NAME_CLEANER.matcher(getPageTitle(page).toLowerCase(Locale.ROOT));
      return m.replaceAll("-");
    }
    public String getPageShortName(String htmlFilePath) {
      return getPageShortName(getPage(htmlFilePath));
    }
  }
}

