import java.io.File;

public class Main {
	
	public Main() {
		File directory = new File("/mnt/c/Users/Braden/Downloads/icpe-data-challenge-jmh-main/timeseries");
		for(String fileName : directory.list()) {
			Benchmark benchmark = new Benchmark(directory.getAbsolutePath() + File.separator + fileName);
			benchmark.forEach(run -> run.generateCriticalChangePointData());
		}
	}
	
	public static void main(String... args) {
		new Main();
	}

}
