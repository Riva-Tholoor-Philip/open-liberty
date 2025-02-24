/*******************************************************************************
 * Copyright (c) 2018, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.app.manager.springboot.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.osgi.framework.Constants;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.app.manager.springboot.container.ApplicationError;
import com.ibm.ws.app.manager.springboot.container.ApplicationTr.Type;

/**
 * A utility class for thinning an uber jar by separating application code in a separate jar
 * and libraries(dependencies) in a zip or a directory.
 */
public class SpringBootThinUtil implements Closeable {
    public static final String SPRING_LIB_INDEX_FILE = "META-INF/spring.lib.index";
    private static final String SPRING_BOOT_LOADER_CLASSPATH = "org/springframework/boot/loader/";
    private static final String LIBERTY_EXEC_JAR_BSN = "wlp.lib.extract";
    private static final String LIBERTY_SERVER_NAME_HEADER = "Server-Name";
    private static final String LIBERTY_ARCHIVE_ROOT_HEADER = "Archive-Root";
    private static final String LIBERTY_LIB_CACHE_PATH = "usr/shared/resources/lib.index.cache/";
    private static final String LIBERTY_SERVERS_PATH = "usr/servers/";
    private static final String LIBERTY_SERVER_DROPINS_SPRING = "/dropins/spring/";
    private static final String LIBERTY_SERVER_DROPINS = "/dropins/";
    private static final String LIBERTY_SPRING_EXT = ".spring";
    private static final String LIBERTY_SERVER_APPS = "/apps/";
    private static final String[] appSearchRoots = new String[] { LIBERTY_SERVER_DROPINS_SPRING, LIBERTY_SERVER_DROPINS, LIBERTY_SERVER_APPS };

    private final JarFile sourceFatJar;
    private final File targetThinJar;
    private final File libIndexCache;
    private final File libIndexCacheParent;
    private final String libertyServer;
    private final String libertyRoot;
    private final String springBootLibPath;
    private final String springBootLibProvidedPath;
    private final StarterFilter springStarterFilter;
    private final List<String> libEntries = new ArrayList<>();

    private static final String[] ZIP_EXTENSIONS = new String[] {
                                                                  "jar",
                                                                  "zip",
                                                                  "ear", "war", "rar",
                                                                  "eba", "esa",
                                                                  "sar"
    };

    private static final String ZIP_EXTENSION_SPRING = "spring";

    private static final TraceComponent tc = Tr.register(SpringBootThinUtil.class);

    public SpringBootThinUtil(File sourceFatJar, File targetThinJar, File libIndexCache) throws IOException {
        this(sourceFatJar, targetThinJar, libIndexCache, null);
    }

    public SpringBootThinUtil(File sourceFatJar, File targetThinJar, File libIndexCache, File libIndexCacheParent) throws IOException {
        validateNotNull(sourceFatJar, targetThinJar, libIndexCache);
        this.sourceFatJar = new JarFile(sourceFatJar);
        this.targetThinJar = targetThinJar;
        this.libIndexCache = libIndexCache;
        this.libIndexCacheParent = libIndexCacheParent;

        SpringBootManifest sbmf = null;
        Manifest mf = this.sourceFatJar.getManifest();
        String bsn = mf.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
        if (LIBERTY_EXEC_JAR_BSN.equals(bsn)) {
            this.libertyServer = mf.getMainAttributes().getValue(LIBERTY_SERVER_NAME_HEADER);
            this.libertyRoot = mf.getMainAttributes().getValue(LIBERTY_ARCHIVE_ROOT_HEADER);
            this.springBootLibPath = null;
            this.springBootLibProvidedPath = null;
            this.springStarterFilter = null;

        } else {
            sbmf = new SpringBootManifest(this.sourceFatJar.getManifest());
            String springBootLibPath = sbmf.getSpringBootLib();
            if (!springBootLibPath.endsWith("/")) {
                springBootLibPath += "/";
            }
            String springBootLibProvidedPath = sbmf.getSpringBootLibProvided();
            if (springBootLibProvidedPath != null && !springBootLibProvidedPath.endsWith("/")) {
                springBootLibProvidedPath += "/";
            }
            this.libertyServer = null;
            this.libertyRoot = null;
            this.springBootLibPath = springBootLibPath;
            this.springBootLibProvidedPath = springBootLibProvidedPath;
            this.springStarterFilter = getStarterFilter(this.sourceFatJar);
        }
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "sourceFatJar: " + sourceFatJar.getAbsolutePath());
            Tr.debug(tc, "targetThinJar: " + targetThinJar.getAbsolutePath());
            Tr.debug(tc, "libIndexCache: " + libIndexCache.getAbsolutePath());
            Tr.debug(tc, "libIndexCacheParent: ", Objects.toString(libIndexCacheParent));
            Tr.debug(tc, "libertyServer: " + libertyServer);
            Tr.debug(tc, "libertyRoot: " + libertyRoot);
            Tr.debug(tc, "springBootLibPath: " + springBootLibPath);
            Tr.debug(tc, "springBootLibProvidedPath: " + springBootLibProvidedPath);
            Tr.debug(tc, "springStarterFilter: " + Objects.toString(springStarterFilter));
        }
    }

    private void validateNotNull(File sourceFatJar, File targetThinJar, File libIndexCache) {
        if (sourceFatJar == null) {
            throw new IllegalStateException("Spring Boot source archive cannot be null");
        }
        if (targetThinJar == null) {
            throw new IllegalStateException("Target thin archive cannot be null");
        }
        if (libIndexCache == null) {
            throw new IllegalStateException("Library cache cannot be null");
        }
    }

    public void execute() throws IOException, NoSuchAlgorithmException {
        if (springStarterFilter == null) {
            // this must be a liberty executable JAR
            executeExtractFromExecutableJar();
        } else {
            executeThin();
        }
    }

    private void executeExtractFromExecutableJar() throws IOException {
        if (this.libertyServer == null) {
            throw new ApplicationError(Type.ERROR_INVALID_PACKAGED_LIBERTY_JAR);
        }

        String root = this.libertyRoot;
        if (root == null) {
            root = "";
        }

        String libCachePath = root + LIBERTY_LIB_CACHE_PATH;
        String serverPath = root + LIBERTY_SERVERS_PATH + this.libertyServer;

        PreThinnedApp preThinned = new PreThinnedApp(libCachePath, serverPath, this.sourceFatJar);
        preThinned.discover();
        preThinned.store(this.libIndexCache, this.targetThinJar);
    }

    private void executeThin() throws IOException, NoSuchAlgorithmException {
        try (JarOutputStream thinJar = new JarOutputStream(new FileOutputStream(targetThinJar), sourceFatJar.getManifest())) {
            Set<String> entryNames = new HashSet<>();
            for (Enumeration<JarEntry> entries = sourceFatJar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (entryNames.add(entry.getName()) && !JarFile.MANIFEST_NAME.equals(entry.getName()) &&
                    !entry.getName().startsWith(SPRING_BOOT_LOADER_CLASSPATH) /* omit spring boot loader classes */) {
                    storeEntry(thinJar, entry);
                }
            }
            addLibIndexFileToThinJar(thinJar);
        }
    }

    private void storeEntry(JarOutputStream thinJar, JarEntry entry) throws IOException, NoSuchAlgorithmException {
        String path = entry.getName();
        boolean isLibPath = isFromLibPath(path);
        boolean isLibProvidedPath = isFromLibProvidedPath(path);
        // check if entry is dependency jar or application class
        if (isLibPath || isLibProvidedPath) {
            if (!springStarterFilter.apply(entry.getName()) && (!isLibProvidedPath || includeLibProvidedPaths())) {
                String hash = hash(sourceFatJar, entry);
                String hashPrefix = hash.substring(0, 2);
                String hashSuffix = hash.substring(2, hash.length());

                //check if the entry is zip entry
                if (isZip(entry)) {
                    if (!hasZipExtension(path)) {
                        path = path + ".jar";
                    }
                    storeLibraryInDir(entry, path, hashPrefix, hashSuffix);
                    String libLine = "/" + path + '=' + hash;
                    libEntries.add(libLine);
                } else {
                    throw new IllegalStateException("The entry " + path + " is not a valid zip.");
                }
            }
        } else {
            try (InputStream is = sourceFatJar.getInputStream(entry)) {
                writeEntry(is, thinJar, path);
            }
        }
    }

    /**
     * Check whether the jar entry is a zip, regardless of the extension.
     *
     * @param entry
     * @return true or false telling if the jar entry is a valid zip
     * @throws IOException
     *
     */

    private boolean isZip(JarEntry entry) throws IOException {
        try (InputStream entryInputStream = sourceFatJar.getInputStream(entry)) {
            try (ZipInputStream zipInputStream = new ZipInputStream(entryInputStream)) {
                ZipEntry ze = zipInputStream.getNextEntry();
                if (ze == null) {
                    return false;
                }
                return true;
            }
        }
    }

    /**
     * Tell if a file name has a zip file type extension.
     *
     * These are: "jar", "zip", "ear", "war", "rar", "eba", "esa",
     * "sar", and "spring".
     *
     *
     * @param name The file name to test.
     *
     * @return True or false telling if the file has one of the
     *         zip file type extensions.
     */
    private static boolean hasZipExtension(String name) {
        int nameLen = name.length();

        // Need '.' plus at least three characters.
        if (nameLen < 4) {
            return false;
        }

        // Need '.' plus at least six characters for ".spring".
        if (nameLen >= 7) {
            if ((name.charAt(nameLen - 7) == '.') &&
                name.regionMatches(true, nameLen - 6, ZIP_EXTENSION_SPRING, 0, 6)) {
                return true;
            }
        }

        if (name.charAt(nameLen - 4) != '.') {
            return false;
        } else {
            for (String ext : ZIP_EXTENSIONS) {
                if (name.regionMatches(true, nameLen - 3, ext, 0, 3)) { // ignore case
                    return true;
                }
            }
            return false;
        }
    }

    private boolean includeLibProvidedPaths() {
        // Always return false for now.
        // May add option to include lib provided paths in the future ... but not now
        return false;
    }

    boolean isFromLibPath(String entryName) {
        return entryName.startsWith(springBootLibPath) && !entryName.endsWith("/");
    }

    boolean isFromLibProvidedPath(String entryName) {
        if (springBootLibProvidedPath != null) {
            return entryName.startsWith(springBootLibProvidedPath) && !entryName.endsWith("/");
        }
        return false;
    }

    protected String hash(JarFile jf, ZipEntry entry) throws IOException, NoSuchAlgorithmException {
        InputStream eis = jf.getInputStream(entry);
        MessageDigest digest = MessageDigest.getInstance("sha-256");
        byte[] buffer = new byte[4096];
        int read = -1;

        while ((read = eis.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        byte[] digested = digest.digest();
        return convertToHexString(digested);
    }

    private static String convertToHexString(byte[] digested) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0; i < digested.length; i++) {
            stringBuffer.append(Integer.toString((digested[i] & 0xff) + 0x100, 16).substring(1));
        }
        return stringBuffer.toString();
    }

    private void storeLibraryInDir(JarEntry entry, String path, String hashPrefix, String hashSuffix) throws IOException, NoSuchAlgorithmException {
        String hashPath = hashPrefix + '/' + hashSuffix;
        String libName = path;
        int lastSlash = libName.lastIndexOf('/');
        if (lastSlash >= 0) {
            libName = libName.substring(lastSlash + 1);
        }

        if (libIndexCacheParent != null) {
            // if there is a parent cache look to see if the lib name exists there
            File libDirParent = new File(libIndexCacheParent, hashPath);
            File libFileParent = new File(libDirParent, libName);
            if (libFileParent.exists()) {
                // no need to store since the lib exists in the parent cache
                return;
            }
        }

        if (!libIndexCache.exists()) {
            libIndexCache.mkdirs();
        }

        File libDir = new File(libIndexCache, hashPath);
        File libFile = new File(libDir, libName);
        if (!libFile.exists()) {
            if (!libDir.exists()) {
                libDir.mkdirs();
            }
            InputStream is = sourceFatJar.getInputStream(entry);
            try (OutputStream libJar = new FileOutputStream(libFile)) {
                copyStream(is, libJar);
            } finally {
                is.close();
            }
        }
    }

    private static void writeEntry(InputStream is, ZipOutputStream zos, String entryName) throws IOException {
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            copyStream(is, zos);
        } finally {
            zos.closeEntry();
        }
    }

    static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[4096];
        int read = -1;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }

    private void addLibIndexFileToThinJar(JarOutputStream thinJar) throws IOException {
        thinJar.putNextEntry(new ZipEntry(SPRING_LIB_INDEX_FILE));
        try {
            for (String libEntry : libEntries) {
                thinJar.write(libEntry.getBytes(StandardCharsets.UTF_8));
                thinJar.write('\n');
            }
        } finally {
            thinJar.closeEntry();
        }
    }

    public static StarterFilter getStarterFilter(JarFile jarFile) {
        return getStarterFilter(stringStream(jarFile));
    }

    public static Stream<String> stringStream(JarFile jarFile) {
        Stream<String> stream = StreamSupport.stream(jarFile.stream().spliterator(), false).map(entry -> entry.getName());
        return stream;
    }

    public enum Container {
        TOMCAT("tomcat-embed-core"),
        JETTY("jetty-io"),
        UNDERTOW("undertow-core"),
        NETTY("netty");

        public final String coreContainerJar;

        private Container(String coreContainerJar) {
            this.coreContainerJar = coreContainerJar;
        }

        public String getCoreContainerJar() {
            return coreContainerJar;
        }
    }

    public static StarterFilter getStarterFilter(Stream<String> entries) {
        final AtomicReference<String> starterRef = new AtomicReference<String>();
        final AtomicReference<String> version = new AtomicReference<String>();
        final AtomicReference<String> embeddedContainer = new AtomicReference<String>();
        final AtomicReference<Container> container = new AtomicReference<Container>();

        entries.forEach(entry -> {
            if (version.get() == null || container.get() == null) {
                String path = entry;
                if (version.get() == null && path.contains("BOOT-INF/lib/spring-boot-") && path.endsWith(".jar")) {
                    version.set(path.substring(path.lastIndexOf("-") + 1, path.lastIndexOf(".")));
                } else if (container.get() == null) {
                    for (Container c : Container.values()) {
                        if (path.contains(c.getCoreContainerJar()) && path.endsWith(".jar")) {
                            container.set(c);
                            break;
                        }
                    }
                    if (container.get() != null) {
                        switch (container.get()) {
                            case TOMCAT:
                                embeddedContainer.set(EmbeddedContainer.TOMCAT);
                                break;

                            case JETTY:
                                embeddedContainer.set(EmbeddedContainer.JETTY);
                                break;

                            case UNDERTOW:
                                embeddedContainer.set(EmbeddedContainer.UNDERTOW);
                                break;

                            case NETTY:
                                embeddedContainer.set(EmbeddedContainer.NETTY);
                                break;

                            default:
                                break;
                        }
                    }
                }
            }
        });

        String embeddedConatinerPrefix = (embeddedContainer.get() != null) ? embeddedContainer.get() : "";
        embeddedConatinerPrefix = (version.get() != null) ? embeddedConatinerPrefix + "-" + version.get() : embeddedConatinerPrefix;

        for (String supportedStarter : EmbeddedContainer.getSupportedStarters()) {
            if (embeddedConatinerPrefix.contains(supportedStarter)) {
                starterRef.set(supportedStarter);
                break;
            }
        }
        String springBootStarter = (starterRef.get() != null) ? starterRef.get() : THE_UNKNOWN_STARTER;
        Set<String> starterArtifactIds = EmbeddedContainer.getStarterArtifactIds(springBootStarter);
        return new StarterFilter(springBootStarter, starterArtifactIds);
    }

    static class PreThinnedApp {
        private final String libCachePath;
        private final String serverPath;
        private final JarFile jarFile;

        private final Map<String, Map<String, List<JarEntry>>> applicationEntries = new HashMap<String, Map<String, List<JarEntry>>>();
        private final List<JarEntry> libCacheEntries = new ArrayList<>();
        private boolean appLocationConfigured;

        PreThinnedApp(String libCachePath, String serverPath, JarFile jarFile) {
            this.libCachePath = libCachePath;
            this.serverPath = serverPath;
            this.jarFile = jarFile;
        }

        void discover() throws IOException {
            if (jarFile.getJarEntry(libCachePath) == null) {
                throw new ApplicationError(Type.ERROR_INVALID_PACKAGED_LIBERTY_JAR);
            }
            if (jarFile.getJarEntry(serverPath) == null) {
                throw new ApplicationError(Type.ERROR_INVALID_PACKAGED_LIBERTY_JAR);
            }

            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                String fullEntryPath = entry.getName();
                if (!fullEntryPath.equals(libCachePath) && fullEntryPath.startsWith(libCachePath)) {
                    libCacheEntries.add(entry);
                }
                if (fullEntryPath.equals(serverPath + "/server.xml")) {
                    parseServerXML(jarFile, entry);
                }
                for (String appsRoot : appSearchRoots) {
                    if (addAppEntry(fullEntryPath, appsRoot, entry)) {
                        break;
                    }
                }
            }

            if (libCacheEntries.isEmpty()) {
                throw new ApplicationError(Type.ERROR_INVALID_PACKAGED_LIBERTY_JAR);
            }

            if (applicationEntries.isEmpty()) {
                throw new ApplicationError(Type.ERROR_APP_NOT_FOUND_INSIDE_PACKAGED_LIBERTY_JAR);
            }

            applicationEntries.forEach((k1, m) -> m.forEach((k2, l) -> l.sort((e1, e2) -> e1.getName().compareTo(e2.getName()))));
        }

        private void parseServerXML(JarFile jarFile, JarEntry entry) throws IOException {
            try (InputStream is = jarFile.getInputStream(entry)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("<springBootApplication")) {
                            this.appLocationConfigured = true;
                            break;
                        }
                    }
                }
            }
        }

        private boolean addAppEntry(String fullEntryPath, String appsRoot, JarEntry entry) {
            String fullAppsRoot = serverPath + appsRoot;
            if (fullEntryPath.startsWith(fullAppsRoot)) {
                if (!fullEntryPath.equals(fullAppsRoot)) {
                    String appName = fullEntryPath.substring(fullAppsRoot.length());
                    int slash = appName.indexOf('/');
                    if (slash >= 0) {
                        appName.substring(0, slash);
                    }
                    Map<String, List<JarEntry>> apps = applicationEntries.computeIfAbsent(appsRoot, (k) -> new LinkedHashMap<>());
                    apps.computeIfAbsent(appName, (k) -> new ArrayList<>()).add(entry);
                }
                return true;
            }
            return false;
        }

        void store(File libCache, File thinApp) throws IOException {
            storeApp(thinApp);
            storeLibCache(libCache);
        }

        private void storeApp(File thinApp) throws IOException {
            // First look for dropins/spring
            Map<String, List<JarEntry>> found = applicationEntries.get(LIBERTY_SERVER_DROPINS_SPRING);
            if (found != null) {
                storeApp(thinApp, found.values().iterator().next());
                return;
            }
            found = applicationEntries.get(LIBERTY_SERVER_DROPINS);
            if (found != null) {
                for (Entry<String, List<JarEntry>> app : found.entrySet()) {
                    if (app.getKey().endsWith(LIBERTY_SPRING_EXT)) {
                        storeApp(thinApp, app.getValue());
                        return;
                    }
                }
            }
            found = applicationEntries.get(LIBERTY_SERVER_APPS);
            if (found != null) {
                for (Entry<String, List<JarEntry>> app : found.entrySet()) {
                    if (appLocationConfigured) {
                        storeApp(thinApp, app.getValue());
                        return;
                    }
                }
            }
            throw new ApplicationError(Type.ERROR_APP_NOT_FOUND_INSIDE_PACKAGED_LIBERTY_JAR);
        }

        private void storeApp(File thinApp, List<JarEntry> entries) throws IOException {
            // if the first application entry is a file then assume it is the application jar
            JarEntry firstAppEntry = entries.get(0);
            if (!firstAppEntry.isDirectory()) {
                copyStream(jarFile.getInputStream(firstAppEntry), new FileOutputStream(thinApp));
            } else {
                // the first entry is a directory, assume that the application is an extracted app with
                // the first entry being the root directory of the application
                String dirAppRoot = firstAppEntry.getName();
                String dirAppRootManifest = dirAppRoot + JarFile.MANIFEST_NAME;
                JarEntry manifestEntry = jarFile.getJarEntry(dirAppRootManifest);
                if (manifestEntry == null) {
                    throw new ApplicationError(Type.ERROR_INVALID_PACKAGED_LIBERTY_JAR);
                }
                Manifest mf = new Manifest(jarFile.getInputStream(manifestEntry));
                try (JarOutputStream thinJar = new JarOutputStream(new FileOutputStream(thinApp), mf)) {
                    for (JarEntry appEntry : entries.subList(1, entries.size())) {
                        String entryName = appEntry.getName();
                        if (entryName.startsWith(dirAppRoot) && !entryName.equals(dirAppRootManifest)) {
                            String path = appEntry.getName().substring(dirAppRoot.length());
                            try (InputStream is = jarFile.getInputStream(appEntry)) {
                                writeEntry(is, thinJar, path);
                            }
                        }
                    }
                }
            }
            try (JarFile verifyThinJar = new JarFile(thinApp)) {
                if (verifyThinJar.getEntry(SPRING_LIB_INDEX_FILE) == null) {
                    throw new IOException(SPRING_LIB_INDEX_FILE);
                }
            }

        }

        private void storeLibCache(File libCache) throws IOException {
            for (JarEntry libEntry : libCacheEntries) {
                if (!libEntry.isDirectory()) {
                    String path = libEntry.getName().substring(libCachePath.length());
                    File libFile = new File(libCache, path);
                    if (!libFile.exists()) {
                        if (!libFile.getParentFile().exists()) {
                            libFile.getParentFile().mkdirs();
                        }
                        InputStream is = jarFile.getInputStream(libEntry);
                        try (OutputStream libJar = new FileOutputStream(libFile)) {
                            copyStream(is, libJar);
                        } finally {
                            is.close();
                        }
                    }
                }
            }
        }
    }

    private static String THE_UNKNOWN_STARTER = "";
    private static final Set<String> emptySet = new HashSet<String>(0);

    public static class StarterFilter implements Function<String, Boolean> {
        final String starterName;
        final Set<String> starterArtifactIds;

        public StarterFilter(String starterName, Set<String> starterArtifactIds) {
            this.starterName = starterName;
            this.starterArtifactIds = starterArtifactIds;
        }

        @Override
        public Boolean apply(String jarName) {
            // return true iff jarName is a starter artifact
            return starterArtifactIds.contains(getArtifactId(jarName));
        }

        @Override
        public String toString() {
            return starterName;
        }
    }

    public static String getArtifactId(String jarName) {
        // jarName :: [<dirPath>/]<artifactId>-<version>.jar
        int idxBegAid = jarName.lastIndexOf('/') + 1;
        int idxEndAid = jarName.lastIndexOf('-') - 1;
        return ((idxBegAid <= idxEndAid) && jarName.endsWith(".jar")) ? jarName.substring(idxBegAid, idxEndAid + 1).toLowerCase() : "";
    }

    /**
     *
     */
    static class EmbeddedContainer {

        public static Set<String> getSupportedStarters() {
            return getStartersToDependentArtifactIdsMap().keySet();
        }

        public static Set<String> getStarterArtifactIds(String starter) {
            Set<String> starterArtifactIds = getStartersToDependentArtifactIdsMap().getOrDefault(starter, null);
            if (null == starterArtifactIds) {
                return emptySet;
            }
            return starterArtifactIds;
        }

        // For now mvn dependencies for embedded container starters are provided here. Each list below is populated
        // manually from the output of the command: "mvn dependency:resolve -f pom.xml"; where pom.xml contains one
        // of the following starter dependencies with the appropriate version.
        //                      <artifactId>spring-boot-starter-tomcat</artifactId>
        //                      <artifactId>spring-boot-starter-undertow</artifactId>
        //                      <artifactId>spring-boot-starter-jetty</artifactId>
        //                      <artifactId>spring-boot-starter-reactor-netty</artifactId>
        //
        // We are only concerned with the Artifact ID of the dependencies in the lists below.  Ignore the versions, etc...
        // The format of the dependencies (groupId:artifactId:jar:version:configuration) listed is due to the output provided by the
        // command "mvn dependency:resolve -f pom.xml", and the desire to not introduce errors by manually removing the unneeded fields.
        private final static List<String> mvnSpringBoot15TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:1.5.10.RELEASE:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:8.5.27:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:8.5.27:compile",
                                                                                           "org.apache.tomcat:tomcat-annotations-api:jar:8.5.27:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:8.5.27:compile");
        private final static List<String> mvnSpringBoot20TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.0.1.RELEASE:compile",
                                                                                           "javax.annotation:javax.annotation-api:jar:1.3.2:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:8.5.29:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:8.5.29:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:8.5.29:compile");
        private final static List<String> mvnSpringBoot21TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.1.2.RELEASE:compile",
                                                                                           "javax.annotation:javax.annotation-api:jar:1.3.2:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.14:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.14:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.14:compile");
        private final static List<String> mvnSpringBoot22TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.2.6.RELEASE:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.33:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.33:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.33:compile");

        private final static List<String> mvnSpringBoot23TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.3.0.RELEASE:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.35:compile",
                                                                                           "org.glassfish:jakarta.el:jar:3.0.3:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.35:compile");

        private final static List<String> mvnSpringBoot24TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.4.0:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.39:compile",
                                                                                           "org.glassfish:jakarta.el:jar:3.0.3:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.39:compile");

        private final static List<String> mvnSpringBoot25TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.5.0:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.46:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.46:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.46:compile");

        private final static List<String> mvnSpringBoot26TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.6.6:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.60:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.60:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.60:compile");

        private final static List<String> mvnSpringBoot27TomcatStarterDeps = Arrays.asList(
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:2.7.1:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.64:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.64:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.64:compile");

        private final static List<String> mvnSpringBoot30TomcatStarterDeps = Arrays.asList(
                                                                                           "org.apache.tomcat.embed:tomcat-embed-websocket:jar:10.1.1:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.1:compile",
                                                                                           "org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.1:compile",
                                                                                           "jakarta.annotation:jakarta.annotation-api:jar:2.1.1:compile",
                                                                                           "org.springframework.boot:spring-boot-starter-tomcat:jar:3.0.0:compile");

        private final static List<String> mvnSpringBoot15JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:1.5.10.RELEASE:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.8.v20171121:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:6.0:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.8.v20171121:compile",
                                                                                          "javax.annotation:javax.annotation-api:jar:1.2:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.8.v20171121:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:6.0:compile",
                                                                                          "javax.servlet:javax.servlet-api:jar:3.1.0:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.8.v20171121:compile",
                                                                                          "javax.websocket:javax.websocket-api:jar:1.0:compile",
                                                                                          "org.mortbay.jasper:apache-el:jar:8.0.33:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.8.v20171121:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.8.v20171121:compile",
                                                                                          "org.ow2.asm:asm:jar:6.0:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.8.v20171121:compile");
        private final static List<String> mvnSpringBoot20JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.0.1.RELEASE:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.9.v20180320:compile",
                                                                                          "javax.servlet:javax.servlet-api:jar:3.1.0:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.9.v20180320:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.9.v20180320:compile",
                                                                                          "javax.annotation:javax.annotation-api:jar:1.3.2:compile",
                                                                                          "org.ow2.asm:asm:jar:6.0:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:6.0:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:6.0:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.9.v20180320:compile",
                                                                                          "javax.websocket:javax.websocket-api:jar:1.0:compile",
                                                                                          "org.mortbay.jasper:apache-el:jar:8.5.24.2:compile");
        private final static List<String> mvnSpringBoot21JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.1.2.RELEASE:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.14.v20181114:compile",
                                                                                          "javax.servlet:javax.servlet-api:jar:4.0.1:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.14.v20181114:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.14.v20181114:compile",
                                                                                          "javax.annotation:javax.annotation-api:jar:1.3.2:compile",
                                                                                          "org.ow2.asm:asm:jar:7.0:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:7.0:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:7.0:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:7.0:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.14.v20181114:compile",
                                                                                          "javax.websocket:javax.websocket-api:jar:1.1:compile",
                                                                                          "org.mortbay.jasper:apache-el:jar:8.5.35.1:compile");
        private final static List<String> mvnSpringBoot22JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.2.6.RELEASE:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.3:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.27.v20200227:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.27.v20200227:compile",
                                                                                          "org.ow2.asm:asm:jar:7.2:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:7.2:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.27.v20200227:compile",
                                                                                          "org.mortbay.jasper:apache-el:jar:8.5.49:compile");
        private final static List<String> mvnSpringBoot23JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.3.0.RELEASE:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.3:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.28.v20200408:compile",
                                                                                          "org.eclipse.jetty:jetty-jndi:jar:9.4.28.v20200408:compile",
                                                                                          "org.ow2.asm:asm:jar:7.2:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:7.2:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:7.2:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:7.2:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.28.v20200408:compile",
                                                                                          "org.glassfish:jakarta.el:jar:3.0.3:compile");
        private final static List<String> mvnSpringBoot24JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.4.0:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.34.v20201102:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.34.v20201102:compile",
                                                                                          "org.ow2.asm:asm:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:9.0:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.34.v20201102:compile",
                                                                                          "org.glassfish:jakarta.el:jar:3.0.3:compile");
        private final static List<String> mvnSpringBoot25JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.5.0:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.46:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-util-ajax:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.41.v20210516:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.41.v20210516:compile",
                                                                                          "org.ow2.asm:asm:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:9.0:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:9.0:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.41.v20210516:compile");

        private final static List<String> mvnSpringBoot26JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.6.6:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.60:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-util-ajax:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.45.v20220203:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.45.v20220203:compile",
                                                                                          "org.ow2.asm:asm:jar:9.2:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:9.2:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:9.2:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:9.2:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.45.v20220203:compile");

        private final static List<String> mvnSpringBoot27JettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:2.7.1:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                          "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.64:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-continuation:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-util-ajax:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-server:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-common:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-api:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-client:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-server-impl:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:9.4.48.v20220622:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:9.4.48.v20220622:compile",
                                                                                          "org.ow2.asm:asm:jar:9.3:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:9.3:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:9.3:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:9.3:compile",
                                                                                          "org.eclipse.jetty.websocket:javax-websocket-client-impl:jar:9.4.48.v20220622:compile");

        private final static List<String> mvnSpringBoot30JettyStarterDeps = Arrays.asList(
                                                                                          "jakarta.transaction:jakarta.transaction-api:jar:2.0.0:compile",
                                                                                          "org.eclipse.jetty:jetty-util:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-plus:jar:11.0.12:compile",
                                                                                          "org.ow2.asm:asm-commons:jar:9.3:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-api:jar:2.1.0:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jetty-api:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-client:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-jndi:jar:11.0.12:compile",
                                                                                          "jakarta.annotation:jakarta.annotation-api:jar:2.1.1:compile",
                                                                                          "org.eclipse.jetty:jetty-annotations:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-core-client:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jakarta-server:jar:11.0.12:compile",
                                                                                          "org.ow2.asm:asm:jar:9.3:compile",
                                                                                          "org.eclipse.jetty:jetty-http:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-servlets:jar:11.0.12:compile",
                                                                                          "org.ow2.asm:asm-analysis:jar:9.3:compile",
                                                                                          "org.slf4j:slf4j-api:jar:2.0.0:compile",
                                                                                          "org.eclipse.jetty:jetty-alpn-client:jar:11.0.12:compile",
                                                                                          "org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.1:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-core-server:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-servlet:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-core-common:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jetty-server:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jakarta-common:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-io:jar:11.0.12:compile",
                                                                                          "jakarta.websocket:jakarta.websocket-client-api:jar:2.1.0:compile",
                                                                                          "org.eclipse.jetty:jetty-server:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-security:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty:jetty-webapp:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jetty-common:jar:11.0.12:compile",
                                                                                          "org.eclipse.jetty.websocket:websocket-jakarta-client:jar:11.0.12:compile",
                                                                                          "org.ow2.asm:asm-tree:jar:9.3:compile",
                                                                                          "jakarta.servlet:jakarta.servlet-api:jar:6.0.0:compile",
                                                                                          "org.eclipse.jetty:jetty-xml:jar:11.0.12:compile",
                                                                                          "org.springframework.boot:spring-boot-starter-jetty:jar:3.0.0:compile",
                                                                                          "org.eclipse.jetty:jetty-servlet:jar:11.0.12:compile");

        private final static List<String> mvnSpringBoot15UndertowStarterDeps = Arrays.asList(
                                                                                             "org.jboss.xnio:xnio-api:jar:3.3.8.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.3.1.Final:compile",
                                                                                             "javax.servlet:javax.servlet-api:jar:3.1.0:compile",
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:1.5.10.RELEASE:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.2_spec:jar:1.0.0.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:1.4.22.Final:compile",
                                                                                             "org.glassfish:javax.el:jar:3.0.0:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:1.1.0.Final:compile",
                                                                                             "io.undertow:undertow-core:jar:1.4.22.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.3.8.Final:runtime",
                                                                                             "io.undertow:undertow-servlet:jar:1.4.22.Final:compile");
        private final static List<String> mvnSpringBoot20UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.0.1.RELEASE:compile",
                                                                                             "io.undertow:undertow-core:jar:1.4.23.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.3.2.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.3.8.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.3.8.Final:runtime",
                                                                                             "io.undertow:undertow-servlet:jar:1.4.23.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.2_spec:jar:1.0.2.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:1.4.23.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:1.1.3.Final:compile",
                                                                                             "javax.servlet:javax.servlet-api:jar:3.1.0:compile",
                                                                                             "org.glassfish:javax.el:jar:3.0.0:compile");
        private final static List<String> mvnSpringBoot21UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.1.2.RELEASE:compile",
                                                                                             "io.undertow:undertow-core:jar:2.0.16.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.3.2.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.3.8.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.3.8.Final:runtime",
                                                                                             "io.undertow:undertow-servlet:jar:2.0.16.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.2_spec:jar:1.0.2.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.0.16.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:1.1.3.Final:compile",
                                                                                             "javax.servlet:javax.servlet-api:jar:4.0.1:compile",
                                                                                             "org.glassfish:javax.el:jar:3.0.0:compile");
        private final static List<String> mvnSpringBoot22UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.2.6.RELEASE:compile",
                                                                                             "io.undertow:undertow-core:jar:2.0.30.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.3.8.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.3.8.Final:runtime",
                                                                                             "io.undertow:undertow-servlet:jar:2.0.30.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.2_spec:jar:1.0.2.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.0.30.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:1.1.4.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.3:compile",
                                                                                             "org.glassfish:jakarta.el:jar:3.0.3:compile");
        private final static List<String> mvnSpringBoot23UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.3.0.RELEASE:compile",
                                                                                             "io.undertow:undertow-core:jar:2.1.0.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.0.Final:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.2.Final:compile",
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.0.Final:runtime",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.1.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.1.0.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.3_spec:jar:2.0.1.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.1.0.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:2.0.0.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.3:compile",
                                                                                             "org.glassfish:jakarta.el:jar:3.0.3:compile");
        private final static List<String> mvnSpringBoot24UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.4.0:compile",
                                                                                             "io.undertow:undertow-core:jar:2.2.2.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.0.Final:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.2.Final:compile",
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.0.Final:runtime",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.1.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.2.2.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.3_spec:jar:2.0.1.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.2.2.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:2.0.0.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                             "org.glassfish:jakarta.el:jar:3.0.3:compile");
        private final static List<String> mvnSpringBoot25UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.5.0:compile",
                                                                                             "io.undertow:undertow-core:jar:2.2.7.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.0.Final:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.2.Final:compile",
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.0.Final:runtime",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.1.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.2.7.Final:compile",
                                                                                             "org.jboss.spec.javax.annotation:jboss-annotations-api_1.3_spec:jar:2.0.1.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.2.7.Final:compile",
                                                                                             "org.jboss.spec.javax.websocket:jboss-websocket-api_1.1_spec:jar:2.0.0.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                             "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.46:compile");

        private final static List<String> mvnSpringBoot26UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.6.6:compile",
                                                                                             "io.undertow:undertow-core:jar:2.2.16.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.3.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.6.Final:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.4.Final:compile",
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.6.Final:runtime",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.1.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.2.16.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.2.16.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                             "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                             "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.60:compile");

        private final static List<String> mvnSpringBoot27UndertowStarterDeps = Arrays.asList(
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:2.7.1:compile",
                                                                                             "io.undertow:undertow-core:jar:2.2.18.Final:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.3.Final:compile",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.7.Final:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.4.Final:compile",
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.7.Final:runtime",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.1.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.2.18.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.2.18.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:4.0.4:compile",
                                                                                             "jakarta.websocket:jakarta.websocket-api:jar:1.1.2:compile",
                                                                                             "org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.64:compile");
        private final static List<String> mvnSpringBoot30UndertowStarterDeps = Arrays.asList(
                                                                                             "org.wildfly.client:wildfly-client-config:jar:1.0.1.Final:compile",
                                                                                             "org.jboss.xnio:xnio-nio:jar:3.8.8.Final:runtime",
                                                                                             "org.jboss.xnio:xnio-api:jar:3.8.8.Final:compile",
                                                                                             "org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.1:compile",
                                                                                             "org.springframework.boot:spring-boot-starter-undertow:jar:3.0.0:compile",
                                                                                             "jakarta.websocket:jakarta.websocket-api:jar:2.1.0:compile",
                                                                                             "org.jboss.logging:jboss-logging:jar:3.4.3.Final:compile",
                                                                                             "jakarta.websocket:jakarta.websocket-client-api:jar:2.1.0:compile",
                                                                                             "jakarta.annotation:jakarta.annotation-api:jar:2.1.1:compile",
                                                                                             "org.wildfly.common:wildfly-common:jar:1.5.4.Final:compile",
                                                                                             "io.undertow:undertow-core:jar:2.3.0.Final:compile",
                                                                                             "org.jboss.threads:jboss-threads:jar:3.5.0.Final:compile",
                                                                                             "io.undertow:undertow-servlet:jar:2.3.0.Final:compile",
                                                                                             "io.undertow:undertow-websockets-jsr:jar:2.3.0.Final:compile",
                                                                                             "jakarta.servlet:jakarta.servlet-api:jar:6.0.0:compile");

        // NOTE that we leave netty itself on the classpath in order to allow WebClient to still be used.
        // We do not filter out reator-core or reactive-streams
        private final static List<String> mvnSpringBoot20NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.0.1.RELEASE:compile");
        private final static List<String> mvnSpringBoot21NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.1.2.RELEASE:compile");
        private final static List<String> mvnSpringBoot22NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.2.6.RELEASE:compile");
        private final static List<String> mvnSpringBoot23NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.3.0.RELEASE:compile");
        private final static List<String> mvnSpringBoot24NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.4.0:compile");
        private final static List<String> mvnSpringBoot25NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.5.0:compile");
        private final static List<String> mvnSpringBoot26NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.6.6:compile");
        private final static List<String> mvnSpringBoot27NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:2.7.1:compile");
        private final static List<String> mvnSpringBoot30NettyStarterDeps = Arrays.asList(
                                                                                          "org.springframework.boot:spring-boot-starter-reactor-netty:jar:3.0.0:compile");

        public static final String TOMCAT = "tomcat";
        public static final String JETTY = "jetty";
        public static final String UNDERTOW = "undertow";
        public static final String LIBERTY = "liberty";
        public static final String NETTY = "netty";

        public static final String SPRING_BOOT_STARTER = "spring-boot-starter";
        public static final String SPRING_BOOT_STARTER_REACTOR = "spring-boot-starter-reactor";

        public static Map<String, Set<String>> getStartersToDependentArtifactIdsMap() {
            return startersToDependentArtifactIdsMap;
        }

        public static boolean isBeta = Boolean.valueOf(System.getProperty("com.ibm.ws.beta.edition"));

        private static final Map<String, Set<String>> startersToDependentArtifactIdsMap;

        static {
            Map<String, Set<String>> theMap = new HashMap<String, Set<String>>();
            theMap.put(starterJarNamePrefix(TOMCAT, "1.5"), loadStarterMvnDeps(mvnSpringBoot15TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.0"), loadStarterMvnDeps(mvnSpringBoot20TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.1"), loadStarterMvnDeps(mvnSpringBoot21TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.2"), loadStarterMvnDeps(mvnSpringBoot22TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.3"), loadStarterMvnDeps(mvnSpringBoot23TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.4"), loadStarterMvnDeps(mvnSpringBoot24TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.5"), loadStarterMvnDeps(mvnSpringBoot25TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.6"), loadStarterMvnDeps(mvnSpringBoot26TomcatStarterDeps));
            theMap.put(starterJarNamePrefix(TOMCAT, "2.7"), loadStarterMvnDeps(mvnSpringBoot27TomcatStarterDeps));
            if (isBeta)
                theMap.put(starterJarNamePrefix(TOMCAT, "3.0"), loadStarterMvnDeps(mvnSpringBoot30TomcatStarterDeps));

            theMap.put(starterJarNamePrefix(JETTY, "1.5"), loadStarterMvnDeps(mvnSpringBoot15JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.0"), loadStarterMvnDeps(mvnSpringBoot20JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.1"), loadStarterMvnDeps(mvnSpringBoot21JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.2"), loadStarterMvnDeps(mvnSpringBoot22JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.3"), loadStarterMvnDeps(mvnSpringBoot23JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.4"), loadStarterMvnDeps(mvnSpringBoot24JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.5"), loadStarterMvnDeps(mvnSpringBoot25JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.6"), loadStarterMvnDeps(mvnSpringBoot26JettyStarterDeps));
            theMap.put(starterJarNamePrefix(JETTY, "2.7"), loadStarterMvnDeps(mvnSpringBoot27JettyStarterDeps));
            if (isBeta)
                theMap.put(starterJarNamePrefix(JETTY, "3.0"), loadStarterMvnDeps(mvnSpringBoot30JettyStarterDeps));

            theMap.put(starterJarNamePrefix(UNDERTOW, "1.5"), loadStarterMvnDeps(mvnSpringBoot15UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.0"), loadStarterMvnDeps(mvnSpringBoot20UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.1"), loadStarterMvnDeps(mvnSpringBoot21UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.2"), loadStarterMvnDeps(mvnSpringBoot22UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.3"), loadStarterMvnDeps(mvnSpringBoot23UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.4"), loadStarterMvnDeps(mvnSpringBoot24UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.5"), loadStarterMvnDeps(mvnSpringBoot25UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.6"), loadStarterMvnDeps(mvnSpringBoot26UndertowStarterDeps));
            theMap.put(starterJarNamePrefix(UNDERTOW, "2.7"), loadStarterMvnDeps(mvnSpringBoot27UndertowStarterDeps));
            if (isBeta)
                theMap.put(starterJarNamePrefix(UNDERTOW, "3.0"), loadStarterMvnDeps(mvnSpringBoot30UndertowStarterDeps));

            theMap.put(starterJarNamePrefix(NETTY, "2.0"), loadStarterMvnDeps(mvnSpringBoot20NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.1"), loadStarterMvnDeps(mvnSpringBoot21NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.2"), loadStarterMvnDeps(mvnSpringBoot22NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.3"), loadStarterMvnDeps(mvnSpringBoot23NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.4"), loadStarterMvnDeps(mvnSpringBoot24NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.5"), loadStarterMvnDeps(mvnSpringBoot25NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.6"), loadStarterMvnDeps(mvnSpringBoot26NettyStarterDeps));
            theMap.put(starterJarNamePrefix(NETTY, "2.7"), loadStarterMvnDeps(mvnSpringBoot27NettyStarterDeps));
            if (isBeta)
                theMap.put(starterJarNamePrefix(NETTY, "3.0"), loadStarterMvnDeps(mvnSpringBoot30NettyStarterDeps));

            startersToDependentArtifactIdsMap = Collections.unmodifiableMap(theMap);
        }

        private static String starterJarNamePrefix(String embeddedContainer, String versionInfo) {
            return embeddedContainer + "-" + versionInfo;
        }

        public static Set<String> loadStarterMvnDeps(List<String> mvnStarterDeps) {
            // mvnDep :: groupId:artifactId:jar:version:scope
            Set<String> starterArtifactIds = new HashSet<String>();
            mvnStarterDeps.forEach(mvnDep -> starterArtifactIds.add(mvnDep.split(":")[1].toLowerCase()));
            return Collections.unmodifiableSet(starterArtifactIds);
        }

    }

    @Override
    public void close() throws IOException {
        sourceFatJar.close();
    };

}
