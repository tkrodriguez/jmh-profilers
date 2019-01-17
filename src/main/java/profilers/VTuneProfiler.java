package profilers;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Aggregator;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.lang.Boolean.parseBoolean;

/**
 * The flight recording profiler enables flight recording for benchmarks and starts recording right away.
 */
public class VTuneProfiler implements ExternalProfiler {

    /**
     * Directory to contain all generated reports.
     */
    private static final String SAVE_FLIGHT_OUTPUT_TO = System.getProperty("jmh.vtune.saveTo", ".");

    private static final String ANALYSIS_TYPE = System.getProperty("jmh.vtune.analysisType", "hotspots");

    private static final String EXTRA_OPTIONS = System.getProperty("jmh.vtune.extraOptions", null);

    private static final String FILE_PREFIX = System.getProperty("jmh.vtune.filePrefix", null);

    private static final boolean QUIET = getBoolean("jmh.vtune.quiet", true);

    static boolean getBoolean(String name, boolean defaultValue) {
        boolean result = defaultValue;
        try {
            String property = System.getProperty(name);
            if (property != null) {
                result = parseBoolean(property);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        return result;
    }

    private String target;

    public VTuneProfiler() {
    }

    @Override
    public Collection<String> addJVMInvokeOptions(BenchmarkParams params) {
        StringBuilder base = new StringBuilder(params.getBenchmark());
        Collection<String> keys = params.getParamsKeys();
        if (keys != null && keys.size() > 0) {
            for (String key : keys) {
                String param = params.getParam(key);
                if (param.length() > 0) {
                    base.append("-").append(param);
                }
            }
        }
        String basename = base.toString().replace(File.separator, "_");
        int id = 0;
        while (true) {
            Path path = Paths.get(SAVE_FLIGHT_OUTPUT_TO).resolve(basename + "-r" + id++ + ANALYSIS_TYPE).toAbsolutePath();
            if (!Files.exists(path)) {
                target = path.toString();
                break;
            }
        }
        ArrayList<String> options = new ArrayList<String>(Arrays.asList("amplxe-cl", "-collect", ANALYSIS_TYPE, "-r", target));
        if (QUIET) {
            options.add("-q");
        }

        if (EXTRA_OPTIONS != null) {
            for (String option : EXTRA_OPTIONS.split(" ")) {
                options.add(option);
            }
        }
        return options;
    }

    @Override
    public Collection<String> addJVMOptions(BenchmarkParams params) {
        return Arrays.asList("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints");
    }

    @Override
    public void beforeTrial(BenchmarkParams benchmarkParams) {

    }

    @Override
    public Collection<? extends Result> afterTrial(BenchmarkResult benchmarkResult, long l, File stdOut, File stdErr) {
        NoResult r = new NoResult("VTune experiment saved to " + target);
        return Collections.singleton(r);
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return true;
    }


    public boolean checkSupport(List<String> msgs) {
        return true;
    }


    public String label() {
        return "vtune";
    }

    @Override
    public String getDescription() {
        return "VTune profiler runs for every benchmark.";
    }

    private class NoResult extends Result<NoResult> {
        private final String output;

        public NoResult(String output) {
            super(ResultRole.SECONDARY, "VTune", of(Double.NaN), "N/A", AggregationPolicy.SUM);

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
            return "VTune Messages:\n--------------------------------------------\n" + output;
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
