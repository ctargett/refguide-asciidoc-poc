package com.lucidworks.docparser;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
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
      //  else: not an absoulte URL...
      String path = uri.getPath(); // could be fragment only URL
      Element linkedPage = pageTree.getPageIfMatch(path);
      if (null != path && null != linkedPage) {
        
        // TODO: use .adoc suffix in links
        // TODO: prepend with some macro we can use in post-processing to get <<LINK>> relative link syntax
        path = pageTree.getPageShortName(linkedPage) + ".html";
        
        // HACKish, to ensure we get clean path + ?query? + fragement
        String fixed = new URI(null, null, path, uri.getQuery(), uri.getFragment()).toString();
        return fixed;
        
      } // else...
      System.err.println("found odd rel link to " + href + " in " + page.toString());
      return href;
      
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
      addMetadata(docOut, "toc", "true");
      sideBar.replaceWith(new TextNode("toc::[]",""));
      // TODO: this currently replaces the entire aside/column/panel if there was one...
      // ...would it be better to leave the other panel text and only remove the div.toc-macro?
      //  examples:
      //    Covered in this section:
      //    Topics covered in this section:
      //    Filters discussed in this section:
      //    Algorithms discussed in this section:
    } else {
      // sanity check if we missed any (multiple TOCs on a page?) ...
      elements = docOut.select("div.toc-macro");
      if (! elements.isEmpty()) {
        System.out.println("MISSED A TOC: " + elements.toString());
        System.exit(-1);
      }
    }
    
    // remove empty bolds
    elements = docOut.getElementsByTag("strong");
    for (Element element : elements) {
      if (!element.hasText()) {
        element.remove();
      }
    }
    elements = docOut.getElementsByTag("em");
    for (Element element : elements) {
      if (!element.hasText()) {
        element.remove();
      }
    }
    
    // remove empty pars
    elements = docOut.getElementsByTag("p");
    for (Element element : elements) {
      if (!element.hasText()) {
        element.remove();
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
          // TODO: other values we should also change here? "powershell" ? "js" ?
          element.attr("class", codeClass);
        } else {
          element.removeAttr("class");
        }
      }
    }
    // replace icon text
    elements = docOut.getElementsByClass("aui-icon");
    for (Element element : elements) {
      //                System.out.println(title + ": replaced Icon");
      element.text("Note:");
    }
    
    // remove divs
    elements = docOut.getElementsByTag("div");
    for (Element element : elements) {
      element.unwrap();
    }
    
    elements = docOut.getElementsByTag("tbody");
    for (Element element : elements) {
      element.unwrap();
    }
    
    // remove breaks
    elements = docOut.getElementsByTag("br");
    for (Element element : elements) {
      element.remove();
    }

    docOut.normalise();
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

