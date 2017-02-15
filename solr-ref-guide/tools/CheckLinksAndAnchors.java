
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

/**  
 * Check various things regarding links in the generated HTML site.
 * <p>
 * Asciidoctor doesn't do a good job of rectifying situations where multiple documents are included in one
 * massive (PDF) document may have identical anchors (either explicitly defined, or implicitly defined because of 
 * section headings).  Asciidoctor also doesn't support linking directly to another (included) document by name, 
 * unless there is an explicit '#fragement' used inthe link.
 * </p>
 * <p>
 * This tool parses the generated HTML site, looking for these situations in order to fail the build -- since the 
 * equivilent PDF will be broken
 * </p>
 * 
 * TODO: This class could also generally check that no (relative) links are broken?
 *
 * @see https://github.com/asciidoctor/asciidoctor/issues/1865
 * @see https://github.com/asciidoctor/asciidoctor/issues/1866
 */
public class CheckLinksAndAnchors {

  public static final class HtmlFileFilter implements FileFilter {
    public boolean accept(File pathname) {
      return pathname.getName().toLowerCase().endsWith("html");
    }
  }
  
  public static void main(String[] args) throws Exception {
    int problems = 0;
    
    if (args.length != 1) {
      System.err.println("usage: CheckLinksAndAnchors <htmldir>");
      System.exit(-1);
    }
    final File htmlDir = new File(args[0]);
    
    final File[] pages = htmlDir.listFiles(new HtmlFileFilter());
    if (0 == pages.length) {
      System.err.println("No HTML Files found, wrong htmlDir? forgot to built the site?");
      System.exit(-1);
    }

    final Map<String,List<File>> knownIds = new HashMap<>();
    final Set<String> problemIds = new HashSet<>(0);
    
    for (File file : pages) {
      //System.out.println("input File URI: " + file.toURI().toString());
      
      final String fileContents = readFile(file.getPath());
      final Document doc = Jsoup.parse(fileContents);
      final Element mainContent = doc.select("#main-content").first();
      if (mainContent == null) {
        throw new RuntimeException(file.getName() + " has no main-content div");
      }

      // Add all of the IDs in this doc to knownIds (and problemIds if needed)
      final Elements nodesWithIds = mainContent.select("[id]");
      for (Element node : nodesWithIds) {
        final String id = node.id();
        assert null != id;
        assert 0 != id.length();

        // special case ids that we ignore
        if (id.equals("preamble") || id.equals("main-content")) {
          continue;
        }
        
        if (knownIds.containsKey(id)) {
          problemIds.add(id);
        } else {
          knownIds.put(id, new ArrayList<File>(1));
        }
        knownIds.get(id).add(file);
      }
      
      // check for (relative) links that don't include a fragment
      final Elements links = mainContent.select("a[href]");
      for (Element link : links) {
        final String href = link.attr("href");
        if (0 == href.length()) {
          problems++;
          System.err.println(file.toURI().toString() + " contains link with empty href");
        }
        try {
          final URI uri = new URI(href);
          if (! uri.isAbsolute()) {
            final String frag = uri.getFragment();
            if (null == frag || "".equals(frag)) {
              // we must have a fragment for intra-page links to work correctly
              problems++;
              System.err.println(file.toURI().toString() + " contains relative link w/o an '#anchor': " + href);
            }
          }
        } catch (URISyntaxException ex) {
          problems++;
          System.err.println(file.toURI().toString() + " contains link w/ invalid syntax: " + href);
          System.err.println(" ... " + ex.toString());
        }
      }
    }

    // if there are problematic ids, report them
    for (String id : problemIds) {
      problems++;
      System.err.println("ID occurs multiple times: " + id);
      for (File file : knownIds.get(id)) {
        System.err.println(" ... " + file.toURI().toString());
      }
    }

    
    if (0 < problems) {
      System.err.println("Total of " + problems + " problems found");
      System.exit(-1);
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
  
}

