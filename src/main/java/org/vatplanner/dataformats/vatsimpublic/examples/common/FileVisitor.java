package org.vatplanner.dataformats.vatsimpublic.examples.common;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileVisitor.class);

    public static final String OPTION_NAME_READ_PATH = "rp";
    public static final String OPTION_NAME_READ_FILTER = "rf";
    public static final String OPTION_NAME_READ_REPORT = "rr";

    private final File basePath;
    private final Pattern filterPattern;

    private final CompressorStreamFactory compressorStreamFactory = new CompressorStreamFactory();
    private final ArchiveStreamFactory archiveStreamFactory = new ArchiveStreamFactory("UTF-8");

    private static final Charset CHARSET_FILE = StandardCharsets.ISO_8859_1;
    private static final Charset CHARSET_ARCHIVE = StandardCharsets.UTF_8;

    private int count = 0;
    private final int reportCount;

    private class NamedBufferedReader {

        String name;
        BufferedReader br;

        public NamedBufferedReader(String name, BufferedReader br) {
            this.name = name;
            this.br = br;
        }
    }

    public FileVisitor(CommandLine parameters) {
        basePath = new File(parameters.getOptionValue(OPTION_NAME_READ_PATH));
        filterPattern = Pattern.compile(parameters.getOptionValue(OPTION_NAME_READ_FILTER, ".*"));
        reportCount = Integer.parseInt(parameters.getOptionValue(OPTION_NAME_READ_REPORT, ""));

        if (!basePath.exists()) {
            throw new IllegalArgumentException("Path does not exist: " + basePath.getAbsolutePath());
        }
    }

    private Collection<File> findAllFiles(File base) {
        Collection<File> files = new ArrayList<>();
        addAllFiles(base, files);
        return files;
    }

    private void addAllFiles(File base, Collection<File> files) {
        if (base.isFile()) {
            files.add(base);
        } else if (base.isDirectory()) {
            for (File file : base.listFiles()) {
                addAllFiles(file, files);
            }
        }
    }

    public void visit(Consumer<BufferedReader> dataConsumer) {
        List<File> files = findAllFiles(basePath)
                .stream()
                .filter(this::matchesFilterPattern)
                .sorted(this::compareFileNames)
                .collect(Collectors.toList());

        for (File file : files) {
            LOGGER.debug("reading " + file.getAbsolutePath());

            try {
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                visitAllParts(dataConsumer, decompress(bis));
            } catch (IOException | CompressorException | ArchiveException ex) {
                LOGGER.error("failed to read " + file.getAbsolutePath(), ex);
            }
        }
    }

    private void visitAllParts(Consumer<BufferedReader> dataConsumer, InputStream is) throws IOException, ArchiveException {
        String detected;
        try {
            detected = ArchiveStreamFactory.detect(is);
        } catch (ArchiveException ex) {
            // most-likely not an archive, otherwise we will fail later anyway
            is.reset();
            InputStreamReader isr = new InputStreamReader(is, CHARSET_FILE);
            BufferedReader br = new BufferedReader(isr);
            dataConsumer.accept(br);
            try {
                br.close();
            } catch (IOException ex2) {
                // ignore
            }
            count();
            return;
        }

        TreeSet<NamedBufferedReader> bufferedReadersSortedByFileName = new TreeSet<>((x, y) -> x.name.compareToIgnoreCase(y.name));

        ArchiveInputStream ais = archiveStreamFactory.createArchiveInputStream(detected, is);
        ArchiveEntry entry;
        while ((entry = ais.getNextEntry()) != null) {
            if (!matchesFilterPattern(entry)) {
                continue;
            }

            byte[] bytes = new byte[(int) entry.getSize()];
            int offset = 0;
            while (offset < bytes.length) {
                offset += ais.read(bytes, offset, bytes.length - offset);
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            InputStreamReader isr = new InputStreamReader(bis, CHARSET_FILE);
            BufferedReader br = new BufferedReader(isr);
            bufferedReadersSortedByFileName.add(new NamedBufferedReader(entry.getName(), br));
        }

        for (NamedBufferedReader namedBufferedReader : bufferedReadersSortedByFileName) {
            dataConsumer.accept(namedBufferedReader.br);
            try {
                namedBufferedReader.br.close();
            } catch (IOException ex2) {
                // ignore
            }
            namedBufferedReader.br = null;
            count();
        }
    }

    private void count() {
        count++;
        if ((reportCount > 0) && (count % reportCount == 0)) {
            LOGGER.info("read " + count + " files");
        }
    }

    private boolean matchesFilterPattern(String name) {
        boolean matches = filterPattern.matcher(name).matches();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace((matches ? "in" : "ex") + "clude file name " + name);
        }

        return matches;
    }

    private boolean matchesFilterPattern(File x) {
        return matchesFilterPattern(x.getName());
    }

    private boolean matchesFilterPattern(ArchiveEntry x) {
        return matchesFilterPattern(x.getName());
    }

    private int compareFileNames(File x, File y) {
        return x.getName().compareToIgnoreCase(y.getName());
    }

    private InputStream decompress(InputStream original) throws CompressorException, IOException {
        String detected;
        try {
            detected = CompressorStreamFactory.detect(original);
        } catch (CompressorException ex) {
            // most-likely not compressed, otherwise we will fail later anyway
            original.reset();
            return original;
        }

        return new BufferedInputStream(compressorStreamFactory.createCompressorInputStream(detected, original));
    }

    public static void addOptions(Options options) {
        options.addOption(Option
                .builder(OPTION_NAME_READ_PATH)
                .longOpt("readpath")
                .hasArg()
                .argName("PATH")
                .desc("path to a single file, an archive file or a directory of either types to read")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_READ_FILTER)
                .longOpt("readfilter")
                .hasArg()
                .argName("REGEX")
                .desc("regular expression to apply as positive filter on filenames; if archives are to be read, regex must match both archive names and names of files in archives to be read")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_READ_REPORT)
                .longOpt("readreport")
                .hasArg()
                .argName("COUNT")
                .desc("logs a status report after reading every COUNT files")
                .build());
    }
}
