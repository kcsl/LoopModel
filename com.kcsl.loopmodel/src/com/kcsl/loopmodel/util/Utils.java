package com.kcsl.loopmodel.util;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;
import com.ensoftcorp.open.c.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.utilities.FormattedSourceCorrespondence;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCGFactory;
import com.kcsl.loopmodel.MemoryVerificationProperties;

public class Utils {
	
	/**
	 * The name pattern for the directory containing the graphs for the processed loops.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The prefix for the folder stating the category.
	 * 2- The {@link Node#addressBits()} corresponding the loop header.
	 * 3- The {@link SourceCorrespondence} serialization for the loop header.
	 * 4- The {@link XCSG#name} corresponding to the {@link #mallocsigntureNode}.
	 */
	private static final String EVENT_GRAPH_DIRECTORY_NAME_PATTERN = "%s@@@%s@@@%s@@@%s";
	
	/**
	 * The name pattern for the CFG graph.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The method name corresponding to the CFG.
	 * 2- The source file where this method is defined.
	 * 3- The number of nodes in this CFG.
	 * 4- The number of edges in this CFG.
	 * 5- The number of conditions in this CFG.
	 * 6- The extension for the file.
	 */
	private static final String CFG_GRAPH_FILE_NAME_PATTERN = "CFG@@@%s@@@%s@@@%s@@@%s@@@%s%s";
	
	/**
	 * The name pattern for the PCG graph.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The method name corresponding to the PCG.
	 * 2- The source file where this method is defined.
	 * 3- The number of nodes in this PCG.
	 * 4- The number of edges in this PCG.
	 * 5- The number of conditions in this PCG.
	 * 6- The extension for the file.
	 */
	private static final String PCG_GRAPH_FILE_NAME_PATTERN = "PCG@@@%s@@@%s@@@%s@@@%s@@@%s%s";
	
	/**
	 * Creates a saves the CFG given <codecfgGraph</code>.
	 * 
	 * @param cfgGraph The {@link Graph} to be saved.
	 * @param methodName A {@link String} corresponding to the function name associated with the <code>cfgGraph</code>.
	 * @param sourceFile The source file for the <code>methodName</code>.
	 * @param markup An instance of {@link Markup} defined on this <code>cfgGraph</code>.
	 * @param displayGraphs Whether to display verification graphs to the user. 
	 */
	private static void saveDisplayCFG(Graph cfgGraph, String methodName, String sourceFile, Markup markup, boolean displayGraphs, File path) {
		long nodes = cfgGraph.nodes().size();
		long edges = cfgGraph.edges().size();
		long conditions = cfgGraph.nodes().tagged(XCSG.ControlFlowCondition).size();
		
		if(displayGraphs){
			DisplayUtil.displayGraph(markup, cfgGraph);
		}
		
				try{
					String cfgFileName = String.format(CFG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions,MemoryVerificationProperties.getGraphImageFileNameExtension());
					SaveUtil.saveGraph(new File(path, cfgFileName), cfgGraph, markup).join();
				} catch (InterruptedException e) {}
	}

	/**
	 * Creates a saves the PCG corresponding to the given <code>cfg</code> and <code>eventNodes</code>.
	 * 
	 * @param cfg The CFG from which the PCG to be created.
	 * @param methodName A {@link String} corresponding to the function name associated with the <code>cfg</code>.
	 * @param sourceFile The source file for the <code>methodName</code>.
	 * @param eventNodes A list of event nodes to be used for {@link PCG} construction from the <code>cfg</code>.
	 * @param markup An instance of {@link Markup} defined on this <code>cfg</code>.
	 * @param displayGraphs  Whether to display verification graphs to the user. 
	 */
	private static void saveDisplayPCG(Q cfg, String methodName, String sourceFile, Q eventNodes, Markup markup, boolean displayGraphs, File path) {
		PCG pcg = PCGFactory.create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), eventNodes);
		Q pcgQ = pcg.getPCG();
		Graph pcgGraph = pcgQ.eval();
		
		// STEP 3A: SAVE PCG
		long nodes = pcgGraph.nodes().size();
		long edges = pcgGraph.edges().size();
		long conditions = 0;
		for(Node node : pcgGraph.nodes()){
			if(pcgGraph.edges(node, NodeDirection.OUT).size() > 1){
				conditions++;
			}
		}
		
		if(displayGraphs){
			DisplayUtil.displayGraph(markup, pcgGraph);
		}
		
				try {
					String pcgFileName = String.format(PCG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions, MemoryVerificationProperties.getGraphImageFileNameExtension());
					SaveUtil.saveGraph(new File(path, pcgFileName), pcgGraph, markup).join();
				} catch (InterruptedException e) {}

	}
	/**
	 * Replaces the '/' with '@' for proper escaping when embedding within a filename.
	 * 
	 * @param string A string to be escaped.
	 * @return An escaped string from the passed <code>string</code>.
	 */
	public static String fixSlashes(String string){
		return string.replace('/', '@');
	}
	
	public static String getCSourceFilePath(Node node) {
		String path = null;
		if (node.hasAttr(XCSG.sourceCorrespondence)) {
			SourceCorrespondence sc = (SourceCorrespondence) node.getAttr(XCSG.sourceCorrespondence);
			if (sc != null) {
				path = sc.sourceFile.getFullPath().toString();
			}
		}
		return path;
	}
	
	public static Long getCSourceLineNumber(Node node) {
		long lineNumber = -1;
		if (node.hasAttr(XCSG.sourceCorrespondence)) {
			SourceCorrespondence sc = (SourceCorrespondence) node.getAttr(XCSG.sourceCorrespondence);
			if (sc != null) {
				lineNumber = sc.startLine;
			}
		}
		return lineNumber;
	}
	
	/**
	 * Returns the starting line of the given node or -1 if the node does not have a
	 * source correspondence
	 */
	public static Long getLineNumber(Node node) {
		if(node.taggedWith(XCSG.Language.C) || node.taggedWith(XCSG.Language.CPP)) {
			return getCSourceLineNumber(node);
		} else {
			long line = -1;
			if (node.hasAttr(XCSG.sourceCorrespondence) && node.getAttr(XCSG.sourceCorrespondence) != null) {
				try {
					line = FormattedSourceCorrespondence.getSourceCorrespondent(node).getStartLineNumber();
				} catch (IOException e) {
					line = -1;
				}
			}
			return line;
		}
	}
	
	public static void saveGraph(Node loopHeader, Node function, Q event1, Q event2, Q callsiteEvents, Path dir, String category, String signatureName) {
		File path = createContainingDirectory(dir,loopHeader,category,signatureName);
	    save(loopHeader, function, event1, event2, callsiteEvents, path);	
	}
	
	private static void save(Node loopHeader, Node function, Q event1,Q event2, Q callsiteEvents,File path) {
		String methodName = function.getAttr(XCSG.name).toString();
		SourceCorrespondence sc = (SourceCorrespondence) function.attr().get(XCSG.sourceCorrespondence);
		String sourceFile = "<external>";
		if(sc != null){
			sourceFile = fixSlashes(sc.toString());
		}
		
		Q cfg = CommonQueries.cfg(function);
		if(cfg.eval().nodes().isEmpty()) {
			return;
		}
		Graph cfgGraph = cfg.eval();			
		
		
		Q eventNodes = event1.union(event2,callsiteEvents);
		

		
		Markup markup = new Markup();
		markup.set(event1, MarkupProperty.NODE_BACKGROUND_COLOR, Color.RED);
		markup.set(event2, MarkupProperty.NODE_BACKGROUND_COLOR, Color.GREEN);
		markup.set(callsiteEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.BLUE);
		
		saveDisplayCFG(cfgGraph, methodName, sourceFile, markup, false, path);
		saveDisplayPCG(cfg, methodName, sourceFile, eventNodes, markup, false, path);
	}
	
	
	private static File createContainingDirectory(Path path, Node loopHeader, String category, String signatureName){
		String sc = FormattedSourceCorrespondence.getSourceCorrespondent(loopHeader).toString();
		//SourceCorrespondence sourceCorrespondence = (SourceCorrespondence) loopHeader.getAttr(XCSG.sourceCorrespondence);
		String sourceCorrespondenceString = "<external>";
		if(sc != null){
			sourceCorrespondenceString = fixSlashes(sc);
		}

		String containingDirectoryName = String.format(EVENT_GRAPH_DIRECTORY_NAME_PATTERN, category, loopHeader.addressBits(), sourceCorrespondenceString, signatureName);
		File currentGraphsOutputDirectory = path.resolve(containingDirectoryName).toFile();
		if(currentGraphsOutputDirectory.exists()) {
			return currentGraphsOutputDirectory;
		}
		if(!currentGraphsOutputDirectory.mkdirs()){
			Log.info("Cannot create directory:" +currentGraphsOutputDirectory.getAbsolutePath());
			return currentGraphsOutputDirectory;
		}
		return currentGraphsOutputDirectory;
	}
	

}
