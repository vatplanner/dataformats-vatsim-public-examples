package org.vatplanner.dataformats.vatsimpublic.examples.dump;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.dataformats.vatsimpublic.entities.TimeSpan;
import org.vatplanner.dataformats.vatsimpublic.entities.status.BarometricPressure;
import org.vatplanner.dataformats.vatsimpublic.entities.status.Connection;
import org.vatplanner.dataformats.vatsimpublic.entities.status.DefaultStatusEntityFactory;
import org.vatplanner.dataformats.vatsimpublic.entities.status.Facility;
import org.vatplanner.dataformats.vatsimpublic.entities.status.Flight;
import org.vatplanner.dataformats.vatsimpublic.entities.status.FlightPlan;
import org.vatplanner.dataformats.vatsimpublic.entities.status.GeoCoordinates;
import org.vatplanner.dataformats.vatsimpublic.entities.status.Member;
import org.vatplanner.dataformats.vatsimpublic.entities.status.Report;
import org.vatplanner.dataformats.vatsimpublic.entities.status.TrackPoint;
import org.vatplanner.dataformats.vatsimpublic.examples.common.FileVisitor;
import org.vatplanner.dataformats.vatsimpublic.graph.GraphImport;
import org.vatplanner.dataformats.vatsimpublic.graph.GraphIndex;
import org.vatplanner.dataformats.vatsimpublic.parser.DataFile;
import org.vatplanner.dataformats.vatsimpublic.parser.DataFileParser;

public class Dump {

    private static final Logger LOGGER = LoggerFactory.getLogger(Dump.class);

    private final DataFileParser parser = new DataFileParser();
    private final GraphImport graphImport = new GraphImport(new DefaultStatusEntityFactory());

    private PrintStream out = System.out;

    private static final String OPTION_NAME_MEMBER_ID = "dm";
    private static final String OPTION_NAME_OUTPUT_FILE = "of";
    private static final String OPTION_NAME_OUTPUT_OVERWRITE = "oo";
    private static final String OPTION_NAME_OUTPUT_APPEND = "oa";
    private static final String OPTION_NAME_HELP = "h";

    private final CommandLine parameters;

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        addOptions(options);
        FileVisitor.addOptions(options);

        CommandLineParser parser = new DefaultParser();
        CommandLine parameters = parser.parse(options, args);

        if (!parameters.hasOption(FileVisitor.OPTION_NAME_READ_PATH) || parameters.hasOption(OPTION_NAME_HELP)) {
            new HelpFormatter().printHelp("dump|dump.sh", options);
            System.exit(1);
        }

        new Dump(parameters).run();
    }

    private static void addOptions(Options options) {
        options.addOption(Option
                .builder(OPTION_NAME_MEMBER_ID)
                .longOpt("dumpmember")
                .hasArg()
                .argName("CID")
                .desc("only dumps the member identified by given VATSIM certificate ID (repeat option for multiple members)")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_OUTPUT_FILE)
                .longOpt("outputfile")
                .hasArg()
                .argName("FILE")
                .desc("writes the resulting dump to given FILE instead of stdout")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_OUTPUT_OVERWRITE)
                .longOpt("overwrite")
                .desc("overwrites the specified output file if it already exists")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_OUTPUT_APPEND)
                .longOpt("append")
                .desc("appends to the specified output file if it already exists")
                .build());

        options.addOption(Option
                .builder(OPTION_NAME_HELP)
                .longOpt("help")
                .desc("displays this help message")
                .build());
    }

    private Dump(CommandLine parameters) {
        this.parameters = parameters;
    }

    private void run() {
        configureOutput(parameters);

        SortedSet<Integer> selectedMemberIds = getSelectedMemberIds(parameters.getOptionValues(OPTION_NAME_MEMBER_ID));
        if (selectedMemberIds.isEmpty()) {
            LOGGER.info("Configured to dump all members; result may be very large!");
        } else {
            LOGGER.info("Configured to dump only members " + selectedMemberIds.stream().sorted().map(Object::toString).collect(Collectors.joining(", ")));
        }

        LOGGER.info("Starting to import data files...");
        new FileVisitor(parameters).visit(this::importDataFile);
        LOGGER.info("Done importing data files, dumping...");

        GraphIndex graphIndex = graphImport.getIndex();

        if (!selectedMemberIds.isEmpty()) {
            selectedMemberIds.stream()
                    .sequential()
                    .map(graphIndex::getMemberByVatsimId)
                    .filter(Objects::nonNull)
                    .forEachOrdered(this::printMember);
        } else {
            graphIndex.getAllMembers()
                    .stream()
                    .sorted((a, b) -> Integer.compare(a.getVatsimId(), b.getVatsimId()))
                    .forEachOrdered(this::printMember);
        }
    }

    private void configureOutput(CommandLine parameters) {
        String outputPath = parameters.getOptionValue(OPTION_NAME_OUTPUT_FILE);
        if (outputPath == null) {
            LOGGER.info("Will print dump to STDOUT.");
            return;
        }

        File file = new File(outputPath);
        boolean shouldAppend = true;
        if (!file.exists()) {
            LOGGER.info("Output file " + outputPath + " will be created");
        } else {
            if (parameters.hasOption(OPTION_NAME_OUTPUT_APPEND)) {
                shouldAppend = true;
                LOGGER.info("Output file " + outputPath + " already exists and will be appended to");
            } else if (parameters.hasOption(OPTION_NAME_OUTPUT_OVERWRITE)) {
                shouldAppend = false;
                LOGGER.info("Output file " + outputPath + " already exists and will be overwritten");
            } else {
                System.err.println("Requested output file " + outputPath + " already exists but no handling has been specified: decide for either --append or --overwrite");
                System.exit(1);
            }
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file, shouldAppend);
        } catch (FileNotFoundException ex) {
            System.err.println("Unable to open output file:");
            ex.printStackTrace();
            System.exit(1);
        }
        out = new PrintStream(fos, true);
    }

    private SortedSet<Integer> getSelectedMemberIds(String[] optionValues) {
        SortedSet<Integer> memberIds = new TreeSet<>(Integer::compareTo);
        if (optionValues != null) {
            for (String optionValue : optionValues) {
                int memberId = Integer.parseInt(optionValue);
                memberIds.add(memberId);
            }
        }
        return memberIds;
    }

    private void importDataFile(BufferedReader contentReader) {
        // TODO: check if it makes sense to parallelize parsing again (import moving segments, ordered by record time)
        DataFile dataFile = parser.parse(contentReader);
        graphImport.importDataFile(dataFile);
    }

    private void printMember(Member member) {
        out.println("\n\n----- Member: " + member.getVatsimId());
        out.println("Facilities:");
        printFacilities(member);

        out.println("\nFlights:");
        printFlights(member);
    }

    private void printFlights(Member member) {
        member.getFlights()
                .stream()
                .sorted(this::compareFlights)
                .forEachOrdered(this::printFlight);
    }

    private int compareFlights(Flight a, Flight b) {
        // earliest time first
        int byEarliestVisibleTime = a.getEarliestVisibleTime().compareTo(b.getEarliestVisibleTime());
        if (byEarliestVisibleTime != 0) {
            return byEarliestVisibleTime;
        }

        // refilings result in same earlierst time for both flights
        // try to fall back to track point time
        SortedSet<TrackPoint> trackA = a.getTrack();
        SortedSet<TrackPoint> trackB = b.getTrack();
        if (!trackA.isEmpty() && !trackB.isEmpty()) {
            Instant timeA = trackA.first().getReport().getRecordTime();
            Instant timeB = trackB.first().getReport().getRecordTime();
            return timeA.compareTo(timeB);
        }

        // try to fall back to flight plan time
        SortedSet<FlightPlan> plansA = a.getFlightPlans();
        SortedSet<FlightPlan> plansB = b.getFlightPlans();
        if (!plansA.isEmpty() && !plansB.isEmpty()) {
            Instant timeA = plansA.first().getReportFirstSeen().getRecordTime();
            Instant timeB = plansB.first().getReportFirstSeen().getRecordTime();
            return timeA.compareTo(timeB);
        }

        return 0;
    }

    private void printFlight(Flight flight) {
        TimeSpan timeSpan = flight.getVisibleTimeSpan();
        out.println(timeSpan.getStart() + "-" + timeSpan.getEnd() + " " + flight.getCallsign() + " " + flight.getMember().getVatsimId());

        flight.getFlightPlans().forEach(this::printFlightPlan);

        SortedSet<TrackPoint> track = flight.getTrack();
        if (!track.isEmpty()) {
            out.println("  track:             time latitude longitude   alt  FL hdg   GS  inHg  hPa xpdr");
            track.forEach(this::printTrackPoint);
        }

        Set<Connection> connections = flight.getConnections();
        if (!connections.isEmpty()) {
            out.println("  connections:      logon           first seen            last seen     server   v base real name");
            for (Connection connection : connections) {
                out.println(String.format("     %20s %20s %20s %10s %3d %4s %s", connection.getLogonTime(), connection.getFirstReport().getRecordTime(), connection.getLastReport().getRecordTime(), connection.getServerId(), connection.getProtocolVersion(), connection.getHomeBase(), connection.getRealName()));
            }
        }

        List<Report> reconstructedReports = flight.getReconstructedReports()
                .stream()
                .sorted(this::compareRecordTime)
                .collect(Collectors.toList());
        if (!reconstructedReports.isEmpty()) {
            out.println("  reconstructed reports: (data files held incomplete or broken information)");
            for (Report reconstructedReport : reconstructedReports) {
                out.println("     " + reconstructedReport.getRecordTime());
            }
        }

        out.println();
    }

    private void printTrackPoint(TrackPoint point) {
        GeoCoordinates coords = point.getGeoCoordinates();
        double latitude = (coords != null) ? coords.getLatitude() : Double.NaN;
        double longitude = (coords != null) ? coords.getLongitude() : Double.NaN;
        int altitudeFeet = (coords != null) ? coords.getAltitudeFeet() : 0;
        int flightLevel = point.getFlightLevel();
        if (flightLevel < 0) {
            flightLevel = 0;
        }

        BarometricPressure qnh = point.getQnh();
        double qnhInHg = (qnh != null) ? qnh.getInchesOfMercury() : Double.NaN;
        int qnhHpa = (int) Math.round((qnh != null) ? qnh.getHectopascals() : 0);

        out.println(String.format("     %20s %8.4f %9.4f %5d %03d %03d %4d %5.2f %4d %04d", point.getReport().getRecordTime(), latitude, longitude, altitudeFeet, flightLevel, point.getHeading(), point.getGroundSpeed(), qnhInHg, qnhHpa, point.getTransponderCode()));
    }

    private void printFlightPlan(FlightPlan flightPlan) {
        out.println("  #" + flightPlan.getRevision() + " " + flightPlan.getReportFirstSeen().getRecordTime());
        out.println("     " + flightPlan.getFlightPlanType() + " " + flightPlan.getDepartureAirportCode() + "-" + flightPlan.getDestinationAirportCode() + "/" + flightPlan.getAlternateAirportCode() + " " + flightPlan.getCommunicationMode() + " " + flightPlan.getDepartureTimePlanned() + " " + flightPlan.getDepartureTimeActual());
        out.println("     alt " + flightPlan.getAltitudeFeet() + ", TAS " + flightPlan.getTrueAirSpeed() + ", enroute " + flightPlan.getEstimatedTimeEnroute() + ", fuel " + flightPlan.getEstimatedTimeFuel());
        out.println("     " + flightPlan.getAircraftType() + " " + flightPlan.getSimpleEquipmentSpecification() + " " + flightPlan.getWakeTurbulenceCategory());
        out.println("     " + flightPlan.getRoute());
        out.println("     " + flightPlan.getRemarks());
    }

    private <T> int compareRecordTime(T a, T b, Function<T, Report> getterMethod) {
        return compareRecordTime(getterMethod.apply(a), getterMethod.apply(b));
    }

    private int compareRecordTime(Report a, Report b) {
        return a.getRecordTime().compareTo(b.getRecordTime());
    }

    private void printFacilities(Member member) {
        member.getFacilities()
                .stream()
                .sorted((a, b) -> compareRecordTime(a, b, x -> x.getConnection().getFirstReport()))
                .forEachOrdered(this::printFacility);
    }

    private void printFacility(Facility x) {
        Connection conn = x.getConnection();
        out.println(conn.getLogonTime() + "/" + conn.getFirstReport().getRecordTime() + " - " + conn.getLastReport().getRecordTime() + " " + x.getName() + " " + x.getFrequencyKilohertz() + " " + x.getType() + " " + conn.getRealName());

        x.getMessages()
                .stream()
                .sorted((a, b) -> a.getReportFirstSeen().getRecordTime().compareTo(b.getReportFirstSeen().getRecordTime()))
                .forEachOrdered(msg -> out.println("    " + msg.getReportFirstSeen().getRecordTime() + " " + msg.getMessage().replace("\n", " | ")));
    }

}
