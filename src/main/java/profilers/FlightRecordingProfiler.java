package profilers;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Aggregator;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// Effectively equivalent to passing jvm args to append for each benchmark. e.g.,
//
//@Fork(jvmArgsAppend =
//{"-XX:+UnlockCommercialFeatures",
//  "-XX:+FlightRecorder",
//  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
//  "-XX:FlightRecorderOptions=settings=./openjdk/jdk1.8.0/jre/lib/jfr/profile.jfc,samplethreads=true"
//})

/**
 * The flight recording profiler enables flight recording for benchmarks and starts recording right away.
 */
public class FlightRecordingProfiler implements ExternalProfiler {

    private String startFlightRecordingOptions = "duration=60s,name=profile,settings=profile,";
    private String flightRecorderOptions = "samplethreads=true,";

    /**
     * Directory to contain all generated reports.
     */
    private static final String SAVE_FLIGHT_OUTPUT_TO = System.getProperty("jmh.jfr.saveTo", ".");

    /**
     * Temporary location to record data
     */
    private final String jfrData;

    /**
     * Holds whether recording is supported (checking the existence of the needed unlocking flag)
     */
    private static final boolean IS_SUPPORTED;

    static {
        IS_SUPPORTED = ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:+UnlockCommercialFeatures");
    }

    public FlightRecordingProfiler() throws IOException {
        jfrData = FileUtils.tempFile("jfrData").getAbsolutePath();
    }

    @Override
    public Collection<String> addJVMInvokeOptions(BenchmarkParams params) {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> addJVMOptions(BenchmarkParams params) {

        startFlightRecordingOptions += "filename=" + jfrData;
        String jfcPath = Paths.get(params.getJvm()).resolve("../../lib/jfr/profile.jfc").normalize().toAbsolutePath().toString();
        startFlightRecordingOptions += ",settings=" + jfcPath;

        return Arrays.asList(
                "-XX:+FlightRecorder",
                "-XX:StartFlightRecording=" + startFlightRecordingOptions,
                "-XX:FlightRecorderOptions=" + flightRecorderOptions);
    }

    @Override
    public void beforeTrial(BenchmarkParams benchmarkParams) {

    }

    static int currentId;

    @Override
    public Collection<? extends Result> afterTrial(BenchmarkResult benchmarkResult, long l, File stdOut, File stdErr) {
        String target = Paths.get(SAVE_FLIGHT_OUTPUT_TO).resolve(benchmarkResult.getParams().getBenchmark() + "-" + currentId++ + ".jfr").toAbsolutePath().toString();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        try {
            FileUtils.copy(jfrData, target);
            pw.println("Flight Recording output saved to " + target);
        } catch (IOException e) {
            e.printStackTrace();
            pw.println("Unable to save flight output to " + target);
            pw.println("Did you miss the system property: -Djmh.jfr.saveTo ?");
        }

        pw.flush();
        pw.close();

        NoResult r = new NoResult(sw.toString());

        return Collections.singleton(r);
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return false;
    }


    public boolean checkSupport(List<String> msgs) {
        msgs.add("Commercial features of the JVM need to be enabled for this profiler.");
        return IS_SUPPORTED;
    }


    public String label() {
        return "jfr";
    }

    @Override
    public String getDescription() {
        return "Java Flight Recording profiler runs for every benchmark.";
    }

    private class NoResult extends Result<NoResult> {
        private final String output;

        public NoResult(String output) {
            super(ResultRole.SECONDARY, "JFR", of(Double.NaN), "N/A", AggregationPolicy.SUM);

            this.output = output;
        }

        @Override
        protected Aggregator<NoResult> getThreadAggregator() {
            return new NoResultAggregator();
        }

        @Override
        protected Aggregator<NoResult> getIterationAggregator() {
            return new NoResultAggregator();
        }

        @Override
        public String extendedInfo() {
            return "JFR Messages:\n--------------------------------------------\n" + output;
        }

        private class NoResultAggregator implements Aggregator<NoResult> {
            @Override
            public NoResult aggregate(Collection<NoResult> results) {
                String output = "";
                for (NoResult r : results) {
                    output += r.output;
                }
                return new NoResult(output);
            }
        }
    }
}
