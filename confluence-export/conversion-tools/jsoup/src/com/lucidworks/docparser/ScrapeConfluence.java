package com.lucidworks.docparser;

import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

/**  
 * Extract body of Confluence page using Jsoup library.
 * This creates an identical (flat) directory structured containing cleaned up documents
 */
public class ScrapeConfluence {
  static final Pattern PRE_CODE_CLASS_PATTERN = Pattern.compile("brush:\\s+([^;]+)");
  
    public static void main(String[] args) 
        throws IOException, FileNotFoundException {
        if (args.length < 2) {
            System.err.println("usage: ScrapeConfluence "
                               + "<indir> <outdir>");
            System.exit(-1);
        }
        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);
        HtmlFileFilter htmlFilter = new HtmlFileFilter();
        File[] pages = inputDir.listFiles(htmlFilter);
        for (File page : pages) {
            System.out.println("input Page URI: " + page.toURI().toString());
            String pageName = cleanFileName(page.getName());
            File outPage = new File(outputDir,pageName + ".html");
            System.out.println("outPage URI: " + outPage.toURI().toString());

            // Confluence encodes &nbsp; as 0xa0.
            // JSoup API doesn't handle this - change to space before parsing Document
            String fileContents = readFile(page.getPath());
            fileContents = fileContents.replace('\u00a0',' ');

            // parse Confluence page
            Document doc = Jsoup.parse(fileContents);
            Element mainContent = doc.select("#main-content").first();
            if (mainContent == null) {
                System.out.println("input file: " + page.getName());
                System.out.println("no main-content div - wtf???");
                continue;
            }

            // create clean HTML page
            Document docOut = Document.createShell(outPage.toURI().toString());
            String title = pageName.replace('-',' ');
            docOut.title(title);

            Element breadcrumbs = doc.select("#breadcrumb-section").first();
            if (breadcrumbs == null) {
                System.out.println(title + ": no breadcrumbs");
            } else {
                Element nav = new Element(Tag.valueOf("nav"),".");
                nav.appendChild(breadcrumbs);
                docOut.body().appendChild(nav);
            }

            Element h1 = new Element(Tag.valueOf("h1"),".");
            h1.text(title);
            docOut.body().appendChild(h1);
            docOut.body().appendChild(mainContent);
            docOut.normalise();

            cleanupContent(docOut);

            // fix links
            Pattern p1 = Pattern.compile("_\\d*\\.html");
            Elements elements = docOut.select("a[href]");
            for (Element element : elements) {
                String href = element.attr("href");
                if (href.contains("display/fusion")) {
                    href= cleanLink(href) + ".html";
                    //                    System.out.println("clean link: " + href);
                    element.attr("href",href);
                } else {
                    Matcher m1 = p1.matcher(href);
                    if (m1.find()) {
                        element.attr("href",m1.replaceFirst(".html"));
                        //                        System.out.println("rel link: " +  element.attr("href"));
                    }
                }
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

    static String cleanFileName(String name) {
        int idx = name.lastIndexOf('_');
        if (idx > 0) return name.substring(0,idx);
        idx = name.lastIndexOf('.');
        if (idx > 0) return name.substring(0,idx);
        return name;
    }

    static String cleanLink(String name) {
        //        System.out.println("in link: " + name);
        int idx = name.indexOf("display/fusion/");
        if (idx < 0) return name;
        int trim = idx + "display/fusion/".length();
        name = name.substring(trim,name.length());
        //        System.out.println("out link: " + name);
        return name.replace('+','-');
    }

  static void cleanupContent(Document docOut) {
    // start cleanup
    Elements elements = null;
    
    // remove side panels (page-internal ToCs)
    Element sideBar = docOut.select("[data-type=aside]").first();
    if (sideBar != null) {
      sideBar.remove();
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
          element.attr("class", codeType.group(1));
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
}

