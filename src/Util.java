import java.util.List;

public class Util {
	
	public static double findMiddle(List<Double> values) {
		if(values.size() % 2 != 0) {
			return values.get(values.size()/2);
		} else {
			return (values.get(values.size()/2) + values.get((values.size()/2)-1)) / 2.0;
		}
	}
	
	public static double getPercentile(double percentile, List<Double> values) {
		int index = (int) Math.ceil(percentile / 100 * values.size());
		return values.get(index-1);
	}

}
