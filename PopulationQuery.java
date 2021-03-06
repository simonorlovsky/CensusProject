import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;

/**
 * This class queries census data to find population densities in different
 * areas of the US.
 */
public class PopulationQuery extends RecursiveAction{

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
	 * The maximum number of CensusGroup objects to examine with one thread
	 */
	public static final int SEQUENTIAL_THRESHOLD = 8;

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
	public static Rectangle[][] grid;

	/**
	 * Array to hold the population of each grid location in version 3
	 */
	public static int[][] v3Grid;

	/**
	 * The lower bound to find the min/max lat/long in the data
	 */
	private int start;

	/**
	 * The upper bound to find the min/max lat/long in the data
	 */
	private int finish;

	/**
	 * The minimum latitude of the US rectangle
	 */
	private static float minimumLatitude = 100;

	/**
	 * The maximum latitude of the US rectangle
	 */
	private static float maximumLatitude;

	/**
	 * The minimum longititude of the US rectangle
	 */
	private static float minimumLongitude;

	/**
	 * The maximum longititude of the US rectangle
	 */
	private static float maximumLongitude = -100;

	/**
	 * Holds the current starting column number of the grid
	 */
	private int startCol;

	/**
	 * Holds the current finishing column number of the grid
	 */
	private int endCol;

	/**
	 * Determines how the compute function should work
	 */
	private static boolean computeSwitch;

	/**
	 *	The version number of the program
	 */
	private static int versionNum;

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

	// public PopulationQuery(int startCol, int endCol, int startRow, int endRow, boolean orientation) {
	// 	this.startCol = startCol;
	// 	this.startRow = startRow;
	// 	this.endCol = endCol;
	// 	this.endRow = endRow;
	// 	this.orientation = orientation;
	// }

	public PopulationQuery(int start, int finish, CensusData data, boolean computeSwitch) {
		this.start = start;
		this.finish = finish;
		this.data = data;
		this.computeSwitch = computeSwitch;
	}

	public PopulationQuery(int startCol,int endCol, boolean computeSwitch) {
		this.startCol = startCol;
		this.endCol = endCol;
		this.computeSwitch = computeSwitch;
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
	 * Overridden compute method, will either find the corners of the US or create the grid,
	 * depending on the value of the computeSwitch variable
	 */
	@Override
	public void compute() {
		// Find the corners of the US rectangle
		if(this.computeSwitch) {
			// Threshold reached, find min/max long/lat sequentially
			if( (this.finish-this.start) < SEQUENTIAL_THRESHOLD) {
				for(int i=this.start; i<this.finish; i++){

					if (this.data.data[i].latitude<this.minimumLatitude){
						this.minimumLatitude = this.data.data[i].latitude;
					}
					if (this.data.data[i].latitude>this.maximumLatitude){
						this.maximumLatitude = this.data.data[i].latitude;
					}
					if (this.data.data[i].longitude<this.minimumLongitude){
						this.minimumLongitude = this.data.data[i].longitude;
					}
					if (this.data.data[i].longitude>this.maximumLongitude){
						this.maximumLongitude = this.data.data[i].longitude;
					}
				}
			}
			else {
				PopulationQuery left = new PopulationQuery(this.start, (this.start+this.finish) / 2, this.data,true);
				PopulationQuery right = new PopulationQuery( (this.finish+this.start) / 2, this.finish, this.data,true);
				left.fork();
				right.compute();
				left.join();
			}
		}
		// Populate the grid
		else {
			float width = (maximumLatitude - minimumLatitude) / (float) grid.length;
			float height = (maximumLongitude - minimumLongitude) / (float) grid[0].length;

			if (this.endCol - this.startCol == 1){

				for (int i=0; i<grid.length; i++){
					Rectangle box = new Rectangle(minimumLatitude+(width*i), minimumLatitude+(width*i), minimumLongitude+(height*this.startCol), minimumLongitude+(height*(this.endCol)));
					grid[i][this.startCol] = box;
				}
			}
			else {
					PopulationQuery left = new PopulationQuery(this.startCol,(this.startCol+this.endCol)/2, false);
					PopulationQuery right = new PopulationQuery((this.startCol+this.endCol)/2, this.endCol, false);
					left.fork();
					right.compute();
					left.join();
			}
		}
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
		this.grid = new Rectangle[rows][cols];

		// Simple and sequential
		if(versionNum == 1) {
			float maxLongitude = -100;
			float minLongitude = 0;
			float maxLatitude = 0;
			float minLatitude = 100;

			for(int i=0; i<this.data.dataSize; i++){
				if(this.data.data[i] == null)
					break;

				if (this.data.data[i].latitude<minLatitude){
					minLatitude = this.data.data[i].latitude;
				}
				if (this.data.data[i].latitude>maxLatitude){
					maxLatitude = this.data.data[i].latitude;
				}
				if (this.data.data[i].longitude<minLongitude){
					minLongitude = this.data.data[i].longitude;
				}
				if (this.data.data[i].longitude>maxLongitude){
					maxLongitude = this.data.data[i].longitude;
				}

			}

			float width = (maxLatitude - minLatitude) / (float) rows;
			float height = (maxLongitude - minLongitude) / (float) cols;

			this.grid = new Rectangle[rows][cols];

			for (int i = 0;i<rows;i++) {
				for (int j = 0;j<cols;j++) {
					Rectangle box = new Rectangle(minLatitude+(width*i), minLatitude+(width*(i)),
																				minLongitude+(height*j),minLongitude+(height*(j+1)));
					grid[i][j] = box;
				}
			}
		}
		// Simple and Parallel
		else if (versionNum == 2) {
			//Corners
			PopulationQuery t = new PopulationQuery(0,this.data.dataSize,this.data,true);

			//After the pool is finished, we will have the correct values for the min/max lat/long
			ForkJoinPool.commonPool().invoke(t);

			//filling in the grid
			PopulationQuery gridThread = new PopulationQuery(0,cols,false);
			ForkJoinPool.commonPool().invoke(gridThread);

		}

		/*
		VERSION 3: SMARTER AND SEQUENTIAL
		*/

		else if (versionNum == 3) {

			//grid containing population numbers related to the grid squares' coordinates
			v3Grid = new int[rows][cols];

			float maxLongitude = -100;
			float minLongitude = 0;
			float maxLatitude = 0;
			float minLatitude = 100;

			for(int i=0; i<this.data.dataSize; i++){
				if(this.data.data[i] == null)
					break;

				if (this.data.data[i].latitude<minLatitude){
					minLatitude = this.data.data[i].latitude;
				}
				if (this.data.data[i].latitude>maxLatitude){
					maxLatitude = this.data.data[i].latitude;
				}
				if (this.data.data[i].longitude<minLongitude){
					minLongitude = this.data.data[i].longitude;
				}
				if (this.data.data[i].longitude>maxLongitude){
					maxLongitude = this.data.data[i].longitude;
				}
			}


			// Initial population of grid
			for (int i =0;i<this.data.dataSize;i++) {

				float curLong = this.data.data[i].longitude;
				float curLat = this.data.data[i].latitude;

				int colNumber = (int) (((this.data.data[i].longitude-minLongitude) / (maxLongitude-minLongitude)) * cols);
				int rowNumber = (int) (((this.data.data[i].latitude-minLatitude) / (maxLatitude-minLatitude)) * rows);

				if(rowNumber >= 149) {
					rowNumber = 148;
				}
				if(colNumber >= 108) {
					colNumber = 107;
				}
				v3Grid[rowNumber][colNumber] += this.data.data[i].population;
			}

			//Agregating sums to the bottom right corner
			for (int i = 0;i<rows;i++) {
				for (int j = 0;j<cols;j++) {
					if(i == 0) {
						if(j == 0) {
							// No data to add
						}
						else {
							v3Grid[i][j] = v3Grid[i][j] + v3Grid[i][j-1];
						}
					}
					else {
						if(j == 0) {
							v3Grid[i][j] = v3Grid[i][j] + v3Grid[i-1][j];
						}
						else {
							v3Grid[i][j] = v3Grid[i][j] + v3Grid[i-1][j] + v3Grid[i][j-1] - v3Grid[i-1][j-1];
						}
					}
				}
			}
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

		if(versionNum == 1 || versionNum == 2) {
			float totalPopulation = 0;
			int curPopulation = 0;
			float percentage = 0;

			Rectangle nwRect = this.grid[n-1][w-1];
			Rectangle seRect = this.grid[s-1][e-1];

			float minLatitude = seRect.right;
			float maxLatitude = nwRect.left;
			float minLongitude = nwRect.top;
			float maxLongitude = seRect.bottom;

			for (int i=0;i<this.data.dataSize;i++) {
				if(this.data.data[i].latitude > minLatitude) {
					if(this.data.data[i].latitude < maxLatitude) {
						if(this.data.data[i].longitude > minLongitude) {
							if(this.data.data[i].longitude <= maxLongitude) {
								curPopulation += this.data.data[i].population;
							}
						}
					}
				}
				totalPopulation += (float) this.data.data[i].population;

			}
			return new Pair<Integer, Float>(curPopulation, .0f + (float)curPopulation/totalPopulation*100);
			// return new Pair<Integer, Float>(0,(float) 0);
		}
		else { //if(versionNum == 3) {
			float totalPopulation = v3Grid[v3Grid.length-1][v3Grid[0].length-1];
			int curPopulation = 0;

			//top case
			if (s==1){
				if (w==1){
					System.out.println(v3Grid[n-1][e-1]);
					curPopulation = v3Grid[n-1][e-1];

				}
				else if(w!=1){
					curPopulation = v3Grid[n-1][e-1] - v3Grid[n-2][w-2];
				}
			}

			//not top case at the western edge
			else {
				if (w==1){
					System.out.println(v3Grid[n-1][e-1]);
					curPopulation = v3Grid[n-1][e-1] - v3Grid[s-2][e-2];

				}
				else if(w!=1){
					curPopulation = v3Grid[n-1][e-1] - v3Grid[s-2][e-2] - v3Grid[n-2][w-2] + v3Grid[s-2][w-2];
				}
			}


			return new Pair<Integer, Float>(curPopulation,(float) (curPopulation/totalPopulation)*100);
		}


	}

	// argument 1: file name for input data: pass this to parse
	// argument 2: number of x-dimension buckets
	// argument 3: number of y-dimension buckets
	// argument 4: -v1, -v2, -v3, -v4, or -v5
	public static void main(String[] args) {
		// Parse the command-line arguments.
		String filename;
		int cols, rows;
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


			// Query the population for this rectangle.
			Pair<Integer, Float> result = pq.singleInteraction(w, s, e, n);
			System.out.printf("Query population: %10d\n", result.getElementA());
			System.out.printf("Percent of total: %10.2f%%\n",
					result.getElementB());
		}
		scanner.close();
	}
}
