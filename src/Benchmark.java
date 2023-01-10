import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class Benchmark implements Iterable<Benchmark.BenchmarkRun> {
	
	private static final double CHANGE_THRESHOLD = 0.3;
	private static final double MEAN_CHANGE_THRESHOLD = 0.5;
	private static final int WINDOW_SIZE = 50;
	private static final int WINDOW_OVERLAP = 10;
	private static final int CLEAN_WINDOWS_REQUIRED = 5;
	
	private BenchmarkRun[] runs = new BenchmarkRun[10];
	private String fileName;
	
	public Benchmark(String fileName) {
		this.fileName = fileName;
		try {
			String json = Files.readString(Path.of(fileName));
			JsonArray allRuns = new JsonParser().parse(json).getAsJsonArray();
			int i = 0;
			for(JsonElement run : allRuns) {
				runs[i] = new BenchmarkRun(new Gson().fromJson(run, double[].class));
				i++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public BenchmarkRun[] getBenchmarkRuns() {
		return runs;
	}
	
	@Override
	public Iterator<Benchmark.BenchmarkRun> iterator() {
		return Arrays.stream(runs).iterator();
	}
	
	
	class BenchmarkRun {
		
		double[] values = new double[3000];
		
		BenchmarkRun(double[] values) {
			this.values = values;
		}
		
		public double[] getValues() {
			return this.values;
		}
		
		public double[] getOutlierBounds(List<Double> values) {
			List<Double> sortedValues = new ArrayList<>(values);
			Collections.sort(sortedValues);
			double outlierRange = 3*(Util.getPercentile(90, sortedValues) - Util.getPercentile(10, sortedValues));
			double lowerBound = Util.findMiddle(sortedValues) - outlierRange;
			double upperBound = Util.findMiddle(sortedValues) + outlierRange;
			
			return new double[] {lowerBound, upperBound};
		}
		
		public void generateCriticalChangePointData() {
			List<Double> means = new ArrayList<>();
			List<Double> coefficientOfVariations = new ArrayList<>();
			for(int i = 0; i < values.length-(WINDOW_SIZE-WINDOW_OVERLAP); i+=(WINDOW_SIZE-WINDOW_OVERLAP)) {
				List<Double> window = DoubleStream.of(values).skip(i).limit(WINDOW_SIZE).boxed().collect(Collectors.toCollection(ArrayList::new));
				double[] outlierBounds = getOutlierBounds(window);
				window.removeIf(value -> (value < outlierBounds[0] || value > outlierBounds[1]));
				double sum = window.stream().mapToDouble(x -> x).sum();
				double mean = sum/window.size(); // Not necessarily equal to WINDOW_SIZE because outliers were removed.
				means.add(mean);
				double standardDev = 0.0;
				for(double value : window) {
					standardDev += Math.pow(value-mean, 2);
				}
				standardDev = Math.sqrt(standardDev);
				double coefficientOfVariation = standardDev/mean;
				coefficientOfVariations.add(coefficientOfVariation);
			}
			List<Double> sortedMeans = new ArrayList<>(means);
			Collections.sort(sortedMeans);
			List<Double> sortedCoefficientOfVariations = new ArrayList<>(coefficientOfVariations);
			Collections.sort(sortedCoefficientOfVariations);
			double meansRange = sortedMeans.get(means.size()-1) - sortedMeans.get(0);
			double coefficientOfVariationsRange = sortedCoefficientOfVariations.get(sortedCoefficientOfVariations.size()-1) - sortedCoefficientOfVariations.get(0);
			double meansThreshold = MEAN_CHANGE_THRESHOLD * meansRange;
			double coefficientOfVariationsThreshold = CHANGE_THRESHOLD * coefficientOfVariationsRange;
			List<Integer> coefficientOfVariationChangePoints = new ArrayList<>();
			for(int j = 1; j < coefficientOfVariations.size(); j++) {
				if(Math.abs(coefficientOfVariations.get(j) - coefficientOfVariations.get(j-1)) > coefficientOfVariationsThreshold) {
					coefficientOfVariationChangePoints.add(j);
					//System.out.println("Found coefficient of variation change point at " + j);
				}
			}
			
			List<Integer> meanChangePoints = new ArrayList<>();
			for(int j = 1; j < means.size(); j++) {
				if(Math.abs(means.get(j) - means.get(j-1)) > meansThreshold) {
					meanChangePoints.add(j);
					//System.out.println("Found means change point at " + j);
				}
			}
			
			List<Integer> combinedChangePoints = new ArrayList<>(coefficientOfVariationChangePoints);
			combinedChangePoints.removeAll(meanChangePoints);
			combinedChangePoints.addAll(meanChangePoints);
			
			int criticalChangePoint = findSteadyStateFromChangePoints(combinedChangePoints) * (WINDOW_SIZE-WINDOW_OVERLAP);
			String output = "";
			if(criticalChangePoint != 0) {
				output = criticalChangePoint + "";
			}
			output = output + "\n";
			try {
				String outputFileName = fileName.replace(".json", ".txt");
				Files.writeString(Path.of(outputFileName), output, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				System.out.println("Wrote to " + outputFileName + ".");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private static int findSteadyStateFromChangePoints(List<Integer> values) {
			if(values.isEmpty()) return 0;
			Collections.sort(values);
			for(int i = 1; i < values.size(); i++) {
				if(values.get(i) - values.get(i-1) >= CLEAN_WINDOWS_REQUIRED) {
					return values.get(i-1);
				}
			}
			return values.get(values.size()-1);
		}

	}

}
