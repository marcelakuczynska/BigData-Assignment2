import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Assignment2Matrix {
    static final int[] SIZES = {20, 40, 80, 120, 160, 200, 320};
    static final int[] SPARSE_SIZES = {80, 160, 240, 320};
    static final int[] ZEROS = {50, 80, 95, 99};
    static final int BLOCK = 32;
    static final int WARMUP = 2;
    static final int RUNS = 5;
    static final long SEED = 20261029L;
    static volatile long used;

    interface Method { Object multiply(long[][] a, long[][] b); }

    static class CSR {
        int n;
        int[] rowPtr, col;
        long[] val;

        CSR(int n, int[] rowPtr, int[] col, long[] val) {
            this.n = n;
            this.rowPtr = rowPtr;
            this.col = col;
            this.val = val;
        }

        int nnz() { return val.length; }
    }

    static long[][] matrix(int n, int zeros, Random random) {
        long[][] a = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (zeros == -1) a[i][j] = random.nextInt(10);
                else if (random.nextInt(100) >= zeros) a[i][j] = 1 + random.nextInt(9);
            }
        }
        return a;
    }

    static long[][] baseline(long[][] a, long[][] b) {
        int n = a.length;
        long[][] c = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                for (int k = 0; k < n; k++)
                    c[i][j] += a[i][k] * b[k][j];
        return c;
    }

    static long[][] reordered(long[][] a, long[][] b) {
        int n = a.length;
        long[][] c = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int k = 0; k < n; k++) {
                long x = a[i][k];
                for (int j = 0; j < n; j++) c[i][j] += x * b[k][j];
            }
        return c;
    }

    static long[][] blocked(long[][] a, long[][] b) {
        int n = a.length;
        long[][] c = new long[n][n];
        for (int ii = 0; ii < n; ii += BLOCK)
            for (int kk = 0; kk < n; kk += BLOCK)
                for (int jj = 0; jj < n; jj += BLOCK)
                    for (int i = ii; i < Math.min(ii + BLOCK, n); i++)
                        for (int k = kk; k < Math.min(kk + BLOCK, n); k++) {
                            long x = a[i][k];
                            for (int j = jj; j < Math.min(jj + BLOCK, n); j++)
                                c[i][j] += x * b[k][j];
                        }
        return c;
    }

    static CSR toCSR(long[][] a) {
        int n = a.length;
        int[] rowPtr = new int[n + 1];
        ArrayList<Integer> cols = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (a[i][j] != 0) {
                    cols.add(j);
                    values.add(a[i][j]);
                }
            }
            rowPtr[i + 1] = cols.size();
        }
        int[] c = new int[cols.size()];
        long[] v = new long[values.size()];
        for (int p = 0; p < c.length; p++) {
            c[p] = cols.get(p);
            v[p] = values.get(p);
        }
        return new CSR(n, rowPtr, c, v);
    }

    static CSR sparseMultiply(CSR a, CSR b) {
        int n = a.n;
        int[] rowPtr = new int[n + 1];
        ArrayList<Integer> cols = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long[] row = new long[n];
            for (int p = a.rowPtr[i]; p < a.rowPtr[i + 1]; p++) {
                int k = a.col[p];
                for (int q = b.rowPtr[k]; q < b.rowPtr[k + 1]; q++)
                    row[b.col[q]] += a.val[p] * b.val[q];
            }
            for (int j = 0; j < n; j++) {
                if (row[j] != 0) {
                    cols.add(j);
                    values.add(row[j]);
                }
            }
            rowPtr[i + 1] = cols.size();
        }
        int[] c = new int[cols.size()];
        long[] v = new long[values.size()];
        for (int p = 0; p < c.length; p++) {
            c[p] = cols.get(p);
            v[p] = values.get(p);
        }
        return new CSR(n, rowPtr, c, v);
    }

    static long[][] toDense(CSR c) {
        long[][] a = new long[c.n][c.n];
        for (int i = 0; i < c.n; i++)
            for (int p = c.rowPtr[i]; p < c.rowPtr[i + 1]; p++)
                a[i][c.col[p]] = c.val[p];
        return a;
    }

    static Object output(String name, long[][] a, long[][] b, CSR ac, CSR bc) {
        return switch (name) {
            case "Baseline" -> baseline(a, b);
            case "Reordered" -> reordered(a, b);
            case "Blocked" -> blocked(a, b);
            case "CSR" -> sparseMultiply(ac, bc);
            default -> throw new IllegalArgumentException(name);
        };
    }

    static long[][] asDense(Object value) {
        if (value instanceof CSR c) return toDense(c);
        return (long[][]) value;
    }

    static int nonzero(long[][] a) {
        int result = 0;
        for (long[] row : a)
            for (long value : row)
                if (value != 0) result++;
        return result;
    }

    static double median(List<Double> values) {
        ArrayList<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    static double iqr(List<Double> values) {
        ArrayList<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return sorted.get((int) Math.floor((n - 1) * 0.75))
             - sorted.get((int) Math.floor((n - 1) * 0.25));
    }

    static void check(long[][] a, long[][] b) {
        if (!Arrays.deepEquals(a, b)) throw new IllegalStateException("Wrong result");
    }

    static void test() {
        long[][] a = {{1, 2}, {3, 4}};
        long[][] b = {{5, 6}, {7, 8}};
        long[][] c = {{19, 22}, {43, 50}};
        long[][] id = {{1, 0}, {0, 1}};
        long[][] zero = {{0, 0}, {0, 0}};
        check(c, baseline(a, b));
        check(c, reordered(a, b));
        check(c, blocked(a, b));
        check(c, toDense(sparseMultiply(toCSR(a), toCSR(b))));
        check(a, baseline(a, id));
        check(zero, baseline(a, zero));
        check(a, toDense(sparseMultiply(toCSR(a), toCSR(id))));
        check(zero, toDense(sparseMultiply(toCSR(a), toCSR(zero))));
    }

    static void benchmark(String mode, String name, int n, int zeros,
                          PrintWriter summary, PrintWriter times, PrintWriter memory) {
        long seed = SEED + 1009L * n + 7919L * zeros;
        Random random = new Random(seed);
        long[][] a = matrix(n, zeros, random);
        long[][] b = matrix(n, zeros, random);
        CSR ac = toCSR(a), bc = toCSR(b);
        long[][] expected = baseline(a, b);
        check(expected, asDense(output(name, a, b, ac, bc)));
        for (int i = 0; i < WARMUP; i++) used += asDense(output(name, a, b, ac, bc))[0][0];
        ArrayList<Double> t = new ArrayList<>();
        ArrayList<Double> mem = new ArrayList<>();
        for (int r = 1; r <= RUNS; r++) {
            System.gc();
            long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long start = System.nanoTime();
            Object result = output(name, a, b, ac, bc);
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            double mib = (after - before) / 1048576.0;
            long[][] c = asDense(result);
            check(expected, c);
            used += c[n - 1][n - 1];
            t.add(ms);
            mem.add(mib);
            times.printf(Locale.US, "%s,%s,%d,%d,%.2f,%d,%.6f,%d,%d,%d%n",
                    mode, name, n, zeros, zeros < 0 ? 100.0 : 100.0 - zeros,
                    r, ms, nonzero(a), nonzero(b), nonzero(c));
            memory.printf(Locale.US, "%s,%s,%d,%d,%d,%.6f%n", mode, name, n, zeros, r, mib);
        }
        int nnzA = nonzero(a), nnzB = nonzero(b), nnzC = nonzero(expected);
        double storage = name.equals("CSR")
                ? (4.0 * (n + 1) * 3 + 12.0 * (ac.nnz() + bc.nnz() + nnzC)) / 1048576.0
                : 3.0 * n * n * 8 / 1048576.0;
        summary.printf(Locale.US,
                "%s,%s,%d,%d,%.2f,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                mode, name, n, zeros, zeros < 0 ? 100.0 : 100.0 - zeros,
                seed, nnzA, nnzB, nnzC, median(t), iqr(t), median(mem), iqr(mem), storage, RUNS);
        System.out.printf(Locale.US, "%s %s n=%d zeros=%d%% time=%.4f ms%n",
                mode, name, n, Math.max(zeros, 0), median(t));
    }

    public static void main(String[] args) throws Exception {
        test();
        Path folder = Path.of("results_assignment2");
        Files.createDirectories(folder);
        try (PrintWriter summary = new PrintWriter(Files.newBufferedWriter(folder.resolve("summary.csv")));
             PrintWriter times = new PrintWriter(Files.newBufferedWriter(folder.resolve("raw_times.csv")));
             PrintWriter memory = new PrintWriter(Files.newBufferedWriter(folder.resolve("raw_memory.csv")))) {
            summary.println("mode,method,n,sparsity_pct,density_pct,seed,nnz_a,nnz_b,nnz_c,median_ms,iqr_ms,median_heap_delta_mib,iqr_heap_delta_mib,estimated_payload_mib,repetitions");
            times.println("mode,method,n,sparsity_pct,density_pct,repetition,time_ms,nnz_a,nnz_b,nnz_c");
            memory.println("mode,method,n,sparsity_pct,repetition,observed_heap_delta_mib");
            System.out.println("Correctness tests passed");
            for (int n : SIZES) {
                benchmark("dense", "Baseline", n, -1, summary, times, memory);
                benchmark("dense", "Reordered", n, -1, summary, times, memory);
                benchmark("dense", "Blocked", n, -1, summary, times, memory);
            }
            for (int n : SPARSE_SIZES)
                for (int zeros : ZEROS) {
                    benchmark("sparse_compare", "Reordered", n, zeros, summary, times, memory);
                    benchmark("sparse_compare", "CSR", n, zeros, summary, times, memory);
                }
        }
        Files.writeString(folder.resolve("Experiment_Setup.txt"),
                "Java=" + System.getProperty("java.version") + "\n" +
                "OS=" + System.getProperty("os.name") + "\n" +
                "Processors=" + Runtime.getRuntime().availableProcessors() + "\n" +
                "Seed formula=20261029+1009*n+7919*sparsity\n" +
                "Dense sizes=" + Arrays.toString(SIZES) + "\n" +
                "Sparse sizes=" + Arrays.toString(SPARSE_SIZES) + "\n" +
                "Zero percentages=" + Arrays.toString(ZEROS) + "\n" +
                "Warmups=" + WARMUP + ", repetitions=" + RUNS + ", block=" + BLOCK + "\n" +
                "Timing=System.nanoTime, multiplication only including output allocation\n" +
                "Memory=approximate Java heap delta before/after each run, NOT peak RSS\n" +
                "Storage=estimated payload of A, B and C, no JVM overhead\n" +
                "Sparse input and output conversion excluded from timing\n" +
                "Methods: baseline i-j-k, reordered i-k-j, blocked, CSR\n");
        System.out.println("DONE. Files in results_assignment2");
        System.out.println("Checksum=" + used);
    }
}
