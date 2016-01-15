import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class queries census data to find population densities in different
 * areas of the US.
 */
public class PopulationQuery {

	/**
	 * For parsing - the number of comma-separated fields on a given line.
	 */
	public static final int TOKENS_PER_LINE = 7;

	/**
	 * For parsing - zero-based index of the field containing the population of
	 * the current census group.
	 */
	public static final int POPULATION_INDEX = 4;

	/**
	 * For parsing - zero-based index of the field containing the latitude of
	 * the current census group.
	 */
	public static final int LATITUDE_INDEX = 5;

	/**
	 * For parsing - zero-based index of the field containing the longitude of
	 * the current census group.
	 */
	public static final int LONGITUDE_INDEX = 6;

	/**
	 * There should be only one fork/join pool per program, so this needs to be
	 * a static variable.
	 */
	private static ForkJoinPool fjPool = new ForkJoinPool();

	/**
	 * Array of census data parsed from the input file.
	 */
	private CensusData data;

	/**
	 * Array that will hold the grid, representing the map of the US
	 */
	private Rectangle[][] grid;

	/**
	 * Initialize the query object by parsing the census data in the given file.
	 *
	 * @param filename
	 *            name of the census data file
	 */
	public PopulationQuery(String filename) {
		// Parse the data and store it in an array.
		this.data = parse(filename);
	}

	/**
	 * Parse the input file into a large array held in a CensusData object.
	 *
	 * @param filename
	 *            name of the file to be used as input.
	 * @return CensusData object containing the parsed data.
	 */
	private static CensusData parse(String filename) {
		CensusData result = new CensusData();

		try {
			BufferedReader fileIn = new BufferedReader(new FileReader(filename));

			/*
			 * Skip the first line of the file. After that, each line has 7
			 * comma-separated numbers (see constants above). We want to skip
			 * the first 4, the 5th is the population (an int) and the 6th and
			 * 7th are latitude and longitude (floats).
			 */

			try {
				/* Skip the first line. */
				String oneLine = fileIn.readLine();

				/*
				 * Read each subsequent line and add relevant data to a big
				 * array.
				 */
				while ((oneLine = fileIn.readLine()) != null) {
					String[] tokens = oneLine.split(",");
					if (tokens.length != TOKENS_PER_LINE)
						throw new NumberFormatException();
					int population = Integer.parseInt(tokens[POPULATION_INDEX]);
					result.add(population,
							Float.parseFloat(tokens[LATITUDE_INDEX]),
							Float.parseFloat(tokens[LONGITUDE_INDEX]));
				}
			} finally {
				fileIn.close();
			}
		} catch (IOException ioe) {
			System.err
					.println("Error opening/reading/writing input or output file.");
			System.exit(1);
		} catch (NumberFormatException nfe) {
			System.err.println(nfe.toString());
			System.err.println("Error in file format");
			System.exit(1);
		}
		return result;
	}

	/**
	 * Preprocess the census data for a run using the given parameters.
	 *
	 * @param cols
	 *            Number of columns in the map grid.
	 * @param rows
	 *            Number of rows in the map grid.
	 * @param versionNum
	 *            implementation to use
	 */
	public void preprocess(int cols, int rows, int versionNum) {

		float maxLongitude = -100;
		float minLongitude = 0;
		float maxLatitude = 0;
		float minLatitude = 100;

		for(int i=0; i<this.data.dataSize; i++){

			if (this.data.data[i].realLatitude<minLatitude){
				// System.out.println(this.data.data[i].latitude);
				minLatitude = this.data.data[i].realLatitude;
			}
			if (this.data.data[i].realLatitude>maxLatitude){
				// System.out.println(this.data.data[i].latitude);
				maxLatitude = this.data.data[i].realLatitude;
			}
			if (this.data.data[i].longitude<minLongitude){
				// System.out.println(this.data.data[i].latitude);
				minLongitude = this.data.data[i].longitude;
			}
			if (this.data.data[i].longitude>maxLongitude){
				// System.out.println(this.data.data[i].latitude);
				maxLongitude = this.data.data[i].longitude;
			}

		}
		/**
		System.out.println("Min latitude: "+minLatitude);
		System.out.println("Max latitude: "+maxLatitude);
		System.out.println("Min longitude: "+minLongitude);
		System.out.println("Max longitude: "+maxLongitude);
		float[] bounds = {minLatitude, maxLatitude, minLongitude, maxLongitude};
		*/

		// Need to set the rectangle width/height according to the max/min lat and longitude

		float width = (maxLatitude - minLatitude) / (float) rows;
		float height = (maxLongitude - minLongitude) / (float) cols;

		this.grid = new Rectangle[rows][cols];
		//System.out.println(this.grid.length+" "+this.grid[0].length);
		for (int i = 0;i<rows;i++) {
			for (int j = 0;j<cols;j++) {
				Rectangle box = new Rectangle(minLatitude+(width*i), minLatitude+(width*(i+1)),
																			minLongitude+(height*j),minLongitude+(height*(j+1)));
				grid[i][j] = box;
				System.out.print("ROW="+i+" COL="+j+"\n"+grid[i][j]+"\n");
			}
			System.out.println();
			System.out.println();
		}
	}

	/**
	 * Query the population of a given rectangle.
	 *
	 * @param w
	 *            western edge of the rectangle
	 * @param s
	 *            southern edge of the rectangle
	 * @param e
	 *            eastern edge of the rectangle
	 * @param n
	 *            northern edge of the rectangle
	 * @return pair containing the population of the rectangle and the
	 *         population as a percentage of the total US population.
	 */
	public Pair<Integer, Float> singleInteraction(int w, int s, int e, int n) {

		int totalPopulation = 0;
		int curPopulation = 0;
		float percentage = 0;

		Rectangle nwRect = this.grid[n][w];
		Rectangle seRect = this.grid[s][e];

		float minLatitude = seRect.bottom;
		float maxLatitude = nwRect.top;
		float minLongitude = nwRect.left;
		float maxLongitude = seRect.right;

		//Find area which we care about
		for (int i=0;i<this.data.data.length;i++) {
			if(this.data.data[i] == null)
				break;
			if (this.data.data[i].realLatitude > minLatitude && this.data.data[i].realLatitude < maxLatitude && this.data.data[i].longitude > minLongitude && this.data.data[i].longitude < maxLongitude) {
				curPopulation += this.data.data[i].population;
			}

			totalPopulation += this.data.data[i].population;

			//if(this.data.data[i].realLatitu)
		}



		return new Pair<Integer, Float>(curPopulation, (float) curPopulation/totalPopulation);
	}

	// argument 1: file name for input data: pass this to parse
	// argument 2: number of x-dimension buckets
	// argument 3: number of y-dimension buckets
	// argument 4: -v1, -v2, -v3, -v4, or -v5
	public static void main(String[] args) {
		// Parse the command-line arguments.
		String filename;
		int cols, rows, versionNum;
		try {
			filename = args[0];
			cols = Integer.parseInt(args[1]);
			rows = Integer.parseInt(args[2]);
			String versionStr = args[3];
			Pattern p = Pattern.compile("-v([12345])");
			Matcher m = p.matcher(versionStr);
			m.matches();
			versionNum = Integer.parseInt(m.group(1));
		} catch (Exception e) {
			System.out
					.println("Usage: java PopulationQuery <filename> <rows> <cols> -v<num>");
			System.exit(1);
			return;
		}

		// Parse the input data.
		PopulationQuery pq = new PopulationQuery(filename);



		// Read queries from stdin.
		Scanner scanner = new Scanner(new BufferedInputStream(System.in));
		while (true) {
			int w, s, e, n;
			try {
				System.out.print("Query? (west south east north | quit) ");
				String west = scanner.next();
				if (west.equals("quit")) {
					break;
				}
				w = Integer.parseInt(west);
				s = scanner.nextInt();
				e = scanner.nextInt();
				n = scanner.nextInt();

				if (w < 1 || w > cols)
					throw new IllegalArgumentException();
				if (e < w || e > cols)
					throw new IllegalArgumentException();
				if (s < 1 || s > rows)
					throw new IllegalArgumentException();
				if (n < s || n > rows)
					throw new IllegalArgumentException();
			} catch (Exception ex) {
				System.out
						.println("Bad input. Please enter four integers separated by spaces.");
				System.out.println("1 <= west <= east <= " + cols);
				System.out.println("1 <= south <= north <= " + rows);
				continue;
			}
			int totalPopulation = 0;
			int population = 0;
			pq.preprocess(cols, rows, versionNum);

			/**for (int i=0; i<pq.data.data.length; i++){
				if (pq.data.data[i]==null){
					break;
				}
				totalPopulation+=pq.data.data[i].population;
				if (pq.data.data[i].realLatitude>=1 && pq.data.data[i].realLatitude<=44 && pq.data.data[i].longitude>=81 && pq.data.data[i].longitude<=149){
					population+=pq.data.data[i].population;
				}
			}
			System.out.println("Population: "+population);
			System.out.println("Percentage: "+population/totalPopulation);
			*/
			// Query the population for this rectangle.
			Pair<Integer, Float> result = pq.singleInteraction(w, s, e, n);
			System.out.printf("Query population: %10d\n", result.getElementA());
			System.out.printf("Percent of total: %10.2f%%\n",
					result.getElementB());
		}
		scanner.close();
	}
}
