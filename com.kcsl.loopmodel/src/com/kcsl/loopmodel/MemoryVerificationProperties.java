package com.kcsl.loopmodel;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class MemoryVerificationProperties {
	
	/**
	 * A {@link String} corresponding to the separator between multi-values in "config.properties" file.
	 */
	private static final String CONFIG_PROPERTIES_FILE_SEPARATOR = ",";
	
	/**
	 * A {@link boolean} flag to indicate whether to save verification graphs in "dot" format.
	 */
	private static boolean SAVE_GRAPH_IN_DOT_FORMAT;
	
	/**
	 * A {@link String} corresponding to image file extension.
	 */
	private static String GRAPH_IMAGE_FILENAME_EXTENSION;
	
	/**
	 * A {@link String} corresponding to dot file extension.
	 */
	private static String GRAPH_DOT_FILENAME_EXTENSION;
	
	/**
	 * A {@link Path} corresponding to the root directory where interactive verification graphs to be saved.
	 */
	private static Path INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH;

	/**
	 * A {@link boolean} flag to indicate whether the feasibility checking is enabled in this verification.
	 */
	private static boolean FEASIBILITY_ENABLED;
	
	/**
	 * A {@link Path} to indicate the root directory where all the verification results will be saved.
	 * <p>
	 * This will be the root directory where all sub-directories and files will be created.
	 */
	private static Path OUTPUT_DIRECTORY;
	
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter C_RESULT_FILE_WRITER;
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter C1_RESULT_FILE_WRITER;
	
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter C2_RESULT_FILE_WRITER;
	
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter C3_RESULT_FILE_WRITER;
	
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER;
	
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_PATH;
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path C_RESULT_FILE_PATH;
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path C1_RESULT_FILE_PATH;
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path C2_RESULT_FILE_PATH;
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path C3_RESULT_FILE_PATH;
	
	/**
	 * A {@link boolean} flag to indicate whether to the save the verification graphs.
	 */
	private static boolean SAVE_VERIFICATION_GRAPHS;
	
	/**
	 * A {@link Path} to be used for saving the memory verification graphs.
	 */
	private static Path C_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A {@link Path} to be used for saving the memory verification graphs.
	 */
	private static Path C1_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A {@link Path} to be used for saving the memory verification graphs.
	 */
	private static Path C2_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A {@link Path} to be used for saving the memory verification graphs.
	 */
	private static Path C3_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A {@link Path} to be used for saving the memory verification graphs.
	 */
	private static Path PROBLEMATIC_LOOP_GRAPHS_OUTPUT_DIRECTORY_PATH;
	
	
	/**
	 * A list of {@link String}s corresponding to the name of malloc function calls.
	 */
	private static List<String> MEM_ALLOC_FUNCTION_CALLS;
	
	/**
	 * A list of {@link String}s corresponding to the name of memory free function calls.
	 */
	private static List<String> MEM_FREE_FUNCTION_CALLS;
	
	private static String RESULT_FILE_HEADER;
	
	private static String PROBLEMATIC_LOOP_FILE_HEADER;
	
	static{
		Properties properties = new Properties();
		InputStream inputStream;
		try {
			inputStream = MemoryVerificationProperties.class.getClassLoader().getResourceAsStream("memconfig.properties");
			properties.load(inputStream);
			FEASIBILITY_ENABLED = Boolean.parseBoolean(properties.getProperty("feasibility_enabled"));
			OUTPUT_DIRECTORY = Paths.get(properties.getProperty("output_directory"));
			checkOrCreatedirectory();
			
			try {
				C_RESULT_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("c_loop_output_result_filename"));
				C_RESULT_FILE_WRITER = new FileWriter(C_RESULT_FILE_PATH.toFile().getAbsolutePath());
			
				C1_RESULT_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("c1_loop_output_result_filename"));
				C1_RESULT_FILE_WRITER = new FileWriter(C1_RESULT_FILE_PATH.toFile().getAbsolutePath());
				
				C2_RESULT_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("c2_loop_output_result_filename"));
				C2_RESULT_FILE_WRITER = new FileWriter(C2_RESULT_FILE_PATH.toFile().getAbsolutePath());
				
				C3_RESULT_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("c3_loop_output_result_filename"));
				C3_RESULT_FILE_WRITER = new FileWriter(C3_RESULT_FILE_PATH.toFile().getAbsolutePath());
				
				PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("problematic_loop_output_result_filename"));
				PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER = new FileWriter(PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_PATH.toFile().getAbsolutePath());
			    
			} catch (IOException e) {
				System.err.println("Cannot open output log file for writing.");
			}
			
			SAVE_VERIFICATION_GRAPHS = Boolean.parseBoolean(properties.getProperty("save_verification_graphs"));
			SAVE_GRAPH_IN_DOT_FORMAT = Boolean.parseBoolean(properties.getProperty("save_graphs_in_dot_format"));
			GRAPH_IMAGE_FILENAME_EXTENSION = properties.getProperty("graph_image_filename_extension");
			GRAPH_DOT_FILENAME_EXTENSION = properties.getProperty("graph_dot_filename_extension");
			INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("interactive_verification_graphs_output_directory_name"));
		
			MEM_ALLOC_FUNCTION_CALLS = Arrays.asList(properties.getProperty("kmalloc").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			MEM_FREE_FUNCTION_CALLS = Arrays.asList(properties.getProperty("kfree").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			C_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("kmalloc_inside_loop_graphs_output_directory_name"));
			C1_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("both_event_inside_loop_graphs_output_directory_name"));
			C2_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("kfree_inside_function_graphs_output_directory_name"));
			C3_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("kfree_not_inside_function_graphs_output_directory_name"));
			PROBLEMATIC_LOOP_GRAPHS_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("problematic_loop_graphs_output_directory_name"));
			
			RESULT_FILE_HEADER = properties.getProperty("loop_file_header");
			PROBLEMATIC_LOOP_FILE_HEADER = properties.getProperty("problematic_loop_file_header");
			C_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C_RESULT_FILE_WRITER.flush();
			C1_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C1_RESULT_FILE_WRITER.flush();
			C2_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C2_RESULT_FILE_WRITER.flush();
			C3_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C3_RESULT_FILE_WRITER.flush();
			
			PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER.write(PROBLEMATIC_LOOP_FILE_HEADER);
			PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot locate the properties file.");
		}
	}
	
	public static boolean isFeasibilityCheckingEnabled(){
		return FEASIBILITY_ENABLED;
	}
	
	public static Path getOutputDirectory(){
		return OUTPUT_DIRECTORY;
	}
	
	public static FileWriter getOutputCResultFileWriter(){
		return C_RESULT_FILE_WRITER;
	}
	
	public static FileWriter getOutputC1ResultFileWriter(){
		return C1_RESULT_FILE_WRITER;
	}
	
	public static FileWriter getOutputC2ResultFileWriter(){
		return C2_RESULT_FILE_WRITER;
	}
	
	public static FileWriter getOutputC3ResultFileWriter(){
		return C3_RESULT_FILE_WRITER;
	}
	
	
	public static FileWriter getOutputProblematicLoopResultFileWriter(){
		return PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER;
	}
	
	
	public static void resetCOutputResultFile() {
		try {		
			C_RESULT_FILE_WRITER = new FileWriter(C_RESULT_FILE_PATH.toFile().getAbsolutePath());
			C_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
	}
	
	public static void resetC1OutputResultFile() {
		try {
			C1_RESULT_FILE_WRITER = new FileWriter(C1_RESULT_FILE_PATH.toFile().getAbsolutePath());
			C1_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C1_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
	}
	
	
	public static void resetC2OutputResultFile() {
		try {
			C2_RESULT_FILE_WRITER = new FileWriter(C2_RESULT_FILE_PATH.toFile().getAbsolutePath());
			C2_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C2_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
	}
	
	
	public static void resetC3OutputResultFile() {
		try {
			C3_RESULT_FILE_WRITER = new FileWriter(C3_RESULT_FILE_PATH.toFile().getAbsolutePath());
			C3_RESULT_FILE_WRITER.write(RESULT_FILE_HEADER);
			C3_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
	}
	
	public static void resetOutputProblematicLoopResultFile() {
		try {
			PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER = new FileWriter(PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_PATH.toFile().getAbsolutePath());
			
			PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER.write(PROBLEMATIC_LOOP_FILE_HEADER);
			PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER.flush();
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
	}

    public static void checkOrCreatedirectory() {
    	if (!OUTPUT_DIRECTORY.toFile().exists()){
			OUTPUT_DIRECTORY.toFile().mkdirs();
		}
    }

	
	public static boolean isSaveVerificationGraphs(){
		return SAVE_VERIFICATION_GRAPHS;
	}
	
	public static boolean saveGraphsInDotFormat(){
		return SAVE_GRAPH_IN_DOT_FORMAT;
	}
	
	public static String getGraphImageFileNameExtension(){
		return GRAPH_IMAGE_FILENAME_EXTENSION;
	}
	
	public static String getGraphDotFileNameExtension(){
		return GRAPH_DOT_FILENAME_EXTENSION;
	}
	
	public static Path getInteractiveVerificationGraphsOutputDirectory(){
		return INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH;
	}
	
	public static List<String> getKMallocFunctionCalls(){
		return MEM_ALLOC_FUNCTION_CALLS;
	}
	
	public static List<String> getKfreeFunctionCalls(){
		return MEM_FREE_FUNCTION_CALLS;
	}
	
	public static Path getCOutputDirectory(){
		return C_OUTPUT_DIRECTORY_PATH;
	}
	
	public static Path getC1OutputDirectory(){
		return C1_OUTPUT_DIRECTORY_PATH;
	}
	public static Path getC2OutputDirectory(){
		return C2_OUTPUT_DIRECTORY_PATH;
	}
	public static Path getC3OutputDirectory(){
		return C3_OUTPUT_DIRECTORY_PATH;
	}
	public static Path getProblematicLoopOutputDirectory(){
		return PROBLEMATIC_LOOP_GRAPHS_OUTPUT_DIRECTORY_PATH;
	}
	public static String getResultCSVHeader(){
		return RESULT_FILE_HEADER;
	}
	public static String getProblematicLoopCSVHeader(){
		return PROBLEMATIC_LOOP_FILE_HEADER;
	}
	public static FileWriter getOutputProblematicLoopFileWriter(){
		return PROBLEMATIC_LOOP_OUTPUT_RESULT_FILE_WRITER;
	}
	public static FileWriter getOutputCFileWriter(){
		return C_RESULT_FILE_WRITER;
	}
	public static FileWriter getOutputC1FileWriter(){
		return C1_RESULT_FILE_WRITER;
	}
	public static FileWriter getOutputC2FileWriter(){
		return C2_RESULT_FILE_WRITER;
	}
	public static FileWriter getOutputC3FileWriter(){
		return C3_RESULT_FILE_WRITER;
	}
	
	
}
