package com.lucidworks.docparser;

import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
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

/**  Extract Breadcrumbs from Confluence page using Jsoup library.
 * 1. create directories - in whatever directory program is called from
 * 2. create map from page name to section/subsection
 *    (can be combined to create page name later)
 */

public class GetSiteMap {
    public static void main(String[] args) 
        throws IOException, FileNotFoundException {
        if (args.length < 2) {
            System.err.println("usage: GetSiteMap <indir> <outdir>");
            System.exit(-1);
        }
        File inDir = new File(args[0]);
        File outDir = new File(args[1]);

        HashSet<String> siteDirs = new HashSet<String>();
        HashMap<String,String> siteMap = new HashMap<String,String>();
        
        HtmlFileFilter htmlFilter = new HtmlFileFilter();
        File[] pages = inDir.listFiles(htmlFilter);
        for (File page : pages) {
            System.out.println("input Page URI: " + page.toURI().toString());

            // Confluence encodes &nbsp; as 0xa0.
            // JSoup API doesn't handle this - change to space before parsing Document
            String fileContents = readFile(page.getPath());
            fileContents = fileContents.replace('\u00a0',' ');
            Document doc = Jsoup.parse(fileContents);
            Element mainContent = doc.select("#main-content").first();
            if (mainContent == null) {
                System.out.println("input file: " + page.getName());
                System.out.println("no main-content div - wtf???");
                continue;
            }

            // find, create all siteDirs, build up map of pages
            StringBuilder sb = new StringBuilder();
            String dirName = null;
            Elements elements = null;
            Element breadcrumbs = doc.select("#breadcrumb-section").first();
            if (breadcrumbs == null) {
                System.out.println(page.getName() + ": no breadcrumbs");
                continue;
            } else {
                elements = breadcrumbs.select("a");
                for (Element element : elements) {
                    String crumb = element.text();
                    if (crumb != null) {
                        crumb = crumb.replace(' ','_');
                        if (sb.length() > 0) sb.append("/");
                        sb.append(crumb);
                        dirName = sb.toString();
                        if (!siteDirs.contains(dirName)) {
                            File aDir = new File(outDir,dirName);
                            if (!aDir.exists()) {
                                System.out.println("creating directory: " + dirName);
                                boolean success = aDir.mkdirs();
                                System.out.println("successful? " + success);
                            } 
                        }
                    }
                }
            }
            String pageName = cleanFileName(page.getName());
            siteMap.put(pageName,dirName);
        }
        // write out sitemap
        File sitemapFile = new File(outDir,"sitemap.txt");
        OutputStream out = new FileOutputStream(sitemapFile);
        Writer writer = new OutputStreamWriter(out,"UTF-8");
        BufferedWriter bufWriter = new BufferedWriter(writer);
        for (Map.Entry<String,String> entry : siteMap.entrySet()) {
            bufWriter.write(entry.getKey() + " = " + entry.getValue() + "\n");
        }
        bufWriter.close();
        writer.close();
        out.close();
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
}
