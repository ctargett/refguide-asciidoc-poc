package com.lucidworks.docparser;

import java.io.File;
import java.io.FileFilter;

public class HtmlFileFilter implements FileFilter {
    public boolean accept(File pathname) {
        return pathname.getName().toLowerCase().endsWith("htm") || pathname.getName().toLowerCase().endsWith("html");
    }
}
