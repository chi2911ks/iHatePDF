/* Generated from Apache POI poi/src/main/version/Version.java.template for REL_5_5_1. */
package org.apache.poi;

public class Version {
    private static final String VERSION_STRING = "5.5.1";
    public static String getVersion() { return VERSION_STRING; }
    public static String getProduct() { return "POI"; }
    public static String getImplementationLanguage() { return "Java"; }
    public static void main(String[] args) {
        System.out.println("Apache " + getProduct() + " " + getVersion());
    }
}
