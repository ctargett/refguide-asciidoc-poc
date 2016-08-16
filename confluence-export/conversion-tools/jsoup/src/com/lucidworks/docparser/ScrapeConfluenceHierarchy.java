package com.lucidworks.docparser;

import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
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
 * Extract body of Confluence page using Jsoup library and organize in hierarchical directories
 * This requres that a sitemap has already been created from the <code>indir</code> by {@link GetSiteMap}
 */
public class ScrapeConfluenceHierarchy extends ScrapeConfluence {
    public static void main(String[] args) 
        throws IOException, FileNotFoundException {
        if (args.length < 3) {
            System.err.println("usage: ScrapeConfluenceHierarchy "
                               + "<indir> <outdir> <sitemap>");
            System.exit(-1);
        }
        File inDir = new File(args[0]);
        File outDir = new File(args[1]);
        File sitemapFilename = new File(args[2]);
        HashMap<String,String> siteMap = new HashMap<String,String>();

        try{
            FileInputStream in = new FileInputStream(sitemapFilename);
            Properties sitemapProps = new Properties();
            sitemapProps.load(in);
            in.close();
            for (Map.Entry<Object,Object> entry : sitemapProps.entrySet()) {
                siteMap.put(entry.getKey().toString(),entry.getValue().toString());
                //                System.out.println(entry.getKey().toString() + " : " + entry.getValue().toString());
            }
        } catch (Exception e) {
            System.err.println("cannot get information from sitemap file: "
                               + args[2]
                               + " exception: "
                               + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }            

        HtmlFileFilter htmlFilter = new HtmlFileFilter();
        File[] pages = inDir.listFiles(htmlFilter);
        for (File page : pages) {
            System.out.println("input Page URI: " + page.toURI().toString());

            // Confluence encodes &nbsp; as 0xa0.
            // JSoup API doesn't handle this - change to space before parsing Document
            String fileContents = readFile(page.getPath());
            fileContents = fileContents.replace('\u00a0',' ');

            // parse Confluence page
            Document doc = Jsoup.parse(fileContents);
            Element mainContent = doc.select("#main-content").first();
            if (mainContent == null) {
                System.out.println("input file: " + page.getName());
                System.out.println("no main-content div - SKIPPING! \n\n");
                continue;
            }

            String pageName = cleanFileName(page.getName());
            String pageDir = siteMap.get(pageName);
            String relPath = "";

            if (pageDir == null) {
                if ("index".equals(pageName))  {
                    pageDir = "/";
                } else {
                    System.out.println("input file: " + pageName + " not in sitemap");
                    System.out.println("SKIPPING! \n\n");
                    continue;
                }
            } else {
                // relative path to documentation root
                // e.g. in page Fusion_Documentation/foo/baz.html has path : ../../FusionDocumentation/
                // e.g. in page Fusion_Documentation/foo/bar/baz.html has path : ../../../FusionDocumentation/
                int nesting = ctOccurs('/',pageDir);
                //            System.out.println("pageDir: " + pageDir + " nesting: " + nesting);
                StringBuilder sb = new StringBuilder();
                for (int i=0; i<= nesting; i++) sb.append("../");
                relPath = sb.toString();
            }
            System.out.println("pageName: " + pageName + "\tpageDir: " + pageDir + "\trelpath: " + relPath );
                
            String outFilename = null;
            outFilename = pageDir + "/" + pageName + ".html";
            File outPage = new File(outDir,outFilename);
            System.out.println("outPage URI: " + outPage.toURI().toString());

            // create clean HTML page
            Document docOut = Document.createShell(outPage.toURI().toString());
            String title = pageName.replace('-',' ');
            
            Map<String,String> metadata = new HashMap<>();
            metadata.put(":page-name", pageName);

            Element breadcrumbs = doc.select("ol#breadcrumbs").first();
            if (breadcrumbs != null) {
              // TODO: add breadcrumb as metadata?
                Element nav = new Element(Tag.valueOf("nav"),".");
                Element curParent = nav;
                Element breadcrumbUl = new Element(Tag.valueOf("ul"),".");
                curParent.appendChild(breadcrumbUl);
                Elements lis = breadcrumbs.select("li");
                for (Element element : lis) {
                    Elements spans = element.getElementsByTag("span");
                    for (Element span : spans) {
                        span.unwrap();
                    }
                    breadcrumbUl.appendChild(element);
                    curParent = element;
                    breadcrumbUl = new Element(Tag.valueOf("ul"),".");
                    curParent.appendChild(breadcrumbUl);
                    System.out.println("li: " + element.toString());
                }
                docOut.body().appendChild(nav);
            }
            
            setMetadata(docOut, title, metadata);

            docOut.body().appendChild(mainContent);
            docOut.normalise();

            cleanupContent(docOut);

            // fix links
            Pattern p1 = Pattern.compile("_\\d*\\.html");
            Elements elements = docOut.select("a[href]");
            for (Element element : elements) {
                String href = element.attr("href");
                String pageNameA = null;
                String pageDirA = "";
                if ("index.html".equals(href)) {
                    pageNameA = relPath + "index.html";
                    System.out.println("mapped index.html link: " +  pageNameA);
                    element.attr("href",pageNameA);
                } else if (href.contains("display/fusion")) {
                    pageNameA = cleanLink(href) + ".html";
                    pageDirA = pageDirA + siteMap.get(pageNameA);
                    if (pageDirA != null) {
                        pageNameA = relPath + pageDirA + "/" + pageNameA;
                    }
                    System.out.println("internal link: " + href + " changed: " +  pageNameA);
                    element.attr("href",pageNameA);
                } else {
                    Matcher m1 = p1.matcher(href);
                    if (m1.find()) {
                        pageNameA = m1.replaceFirst("");
                        pageDirA = pageDirA + siteMap.get(pageNameA);
                        if (pageDirA != null) {
                            pageNameA = relPath + pageDirA + "/" + pageNameA + ".html";
                        }
                        System.out.println("rel link: " + href + " changed: " +  pageNameA);
                        element.attr("href",pageNameA);
                    } else {
                        // System.out.println("other link: " + href);
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

    static int ctOccurs(char chr, String str) {
        int result = 0;
        int idx = str.indexOf(chr);
        while (idx >= 0) {
            result++;
            if (idx == str.length()-1) return result;
            idx = str.indexOf(chr,++idx);
        }
        return result;
    }
}
