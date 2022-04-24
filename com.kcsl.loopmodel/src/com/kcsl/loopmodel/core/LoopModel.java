package com.kcsl.loopmodel.core;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.c.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.algorithms.DominanceAnalysis;
import com.ensoftcorp.open.commons.analysis.SetDefinitions;
import com.kcsl.loopmodel.MemoryVerificationProperties;
import com.kcsl.loopmodel.util.Utils;

public class LoopModel {
	Q nodes = Common.empty();
	Q kmalloc = Common.empty();
	Q kfree = Common.empty();
	private AtlasSet<Node> bothEventInsideLoopContainingFunctions;
	private AtlasSet<Node> firstEventInsideLoopContainingFunctions;
	private AtlasSet<Node> firstEventInsideLoopSecondEventInsideFunctionContainingFunctions;
	private AtlasSet<Node> firstEventInsideLoopSecondEventNotInsideFunctionContainingFunctions;
	private Q callEdges = Common.empty();
	private Q cfEdges = Common.empty();
	private Q invokedEdges = Common.empty();
	private String event1 = "kmalloc";
	private String event2 = "kfree";
	private AtlasSet<Node> firstEventContainingFunctions = new AtlasHashSet<Node>();
	private Q dominanceFrontierEdges = Common.empty();
	long problematicLoopNum = 0L;
	long firstEeventInsideLoopNum = 0L;
	long bothEventInsideLoopNum = 0L;
	long firstEeventInsideLoopSecondInsideFunctionNum = 0L;
	long firstEeventInsideLoopSecondNotInsideFunctionNum = 0L;
	AtlasSet<Node> kmalloconly;
	AtlasSet<Node> kfreeonly;
	private AtlasSet<Node> funs = new AtlasHashSet<Node>();
	private AtlasSet<Node> problematicFuns = new AtlasHashSet<Node>();
	private boolean saveGraph = false;

	LoopModel() {
		saveGraph = false;
		nodes = SetDefinitions.app().contained();
		bothEventInsideLoopContainingFunctions = new AtlasHashSet<Node>();
		firstEventInsideLoopContainingFunctions = new AtlasHashSet<Node>();
		firstEventInsideLoopSecondEventInsideFunctionContainingFunctions = new AtlasHashSet<Node>();
		firstEventInsideLoopSecondEventNotInsideFunctionContainingFunctions = new AtlasHashSet<Node>();
		callEdges = Query.universe().edges(XCSG.Call);
		cfEdges = Query.universe().edges(XCSG.ControlFlow_Edge);
		dominanceFrontierEdges = DominanceAnalysis.getDominanceFrontierEdges();
		invokedEdges = Common.universe().edges(XCSG.InvokedFunction, XCSG.InvokedSignature);
		preprocess();
	}

	public static void verify() {
		LoopModel model = new LoopModel();
		MemoryVerificationProperties.checkOrCreatedirectory();
		MemoryVerificationProperties.resetCOutputResultFile();
		MemoryVerificationProperties.resetC1OutputResultFile();
		MemoryVerificationProperties.resetC2OutputResultFile();
		MemoryVerificationProperties.resetC3OutputResultFile();
		MemoryVerificationProperties.resetOutputProblematicLoopResultFile();
		AtlasSet<Node> loops = model.nodes.nodes(XCSG.Loop).eval().nodes();
		for (Node loopHeader : loops) {
			model.verifyLoop(loopHeader);
		}

		System.out.println("#Functions: " + model.nodes.nodes(XCSG.Function).eval().nodes().size());
		System.out.println("#Functions have loops: " + model.funs.size());
		System.out.println("#Functions have problematic loops: " + model.problematicFuns.size());
		System.out.println("#Total Loops: " + loops.size());
		System.out.println("#Total Problemtaic Loops: " + model.problematicLoopNum);
		System.out.println("#Total Loops having first event inside loop: " + model.firstEeventInsideLoopNum);
		System.out.println("#Total Loops having both events inside loop: " + model.bothEventInsideLoopNum);
		System.out.println("#Total Loops having first event inside loop and second event inside function: "
				+ model.firstEeventInsideLoopSecondInsideFunctionNum);
		System.out.println("#Total Loops having first event inside loop and second event not inside the function: "
				+ model.firstEeventInsideLoopSecondNotInsideFunctionNum);

		System.out.println("#Functions Containing first events: " + model.firstEventContainingFunctions.size());
		System.out.println("#Functions Containing first events inside loops: "
				+ model.firstEventInsideLoopContainingFunctions.size());
		System.out.println("#Functions Containing both events inside loops: "
				+ model.bothEventInsideLoopContainingFunctions.size());
		System.out.println("#Functions Containing first event inside Loop Second Event Inside Function: "
				+ model.firstEventInsideLoopSecondEventInsideFunctionContainingFunctions.size());
		System.out.println("#Functions Containing first Event Inside Loop Second Event Not Inside Function: "
				+ model.firstEventInsideLoopSecondEventNotInsideFunctionContainingFunctions.size());
		try {
			MemoryVerificationProperties.getOutputProblematicLoopResultFileWriter().close();
			MemoryVerificationProperties.getOutputCFileWriter().close();
			MemoryVerificationProperties.getOutputC1FileWriter().close();
			MemoryVerificationProperties.getOutputC2FileWriter().close();
			MemoryVerificationProperties.getOutputC3FileWriter().close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private Node isLoopProblematic(Q loopBody, Node header) {
		Node problematicNode = null;
		AtlasSet<Node> loopMembers = loopBody.eval().nodes();

		for (Node loopMember : loopMembers) {
			if (loopMember.equals(header))
				continue;
			AtlasSet<Node> preds = cfEdges.predecessors(Common.toQ(loopMember)).eval().nodes();

			for (Node pred : preds) {
				if (!loopMembers.contains(pred)) {
					problematicNode = loopMember;
					// DisplayUtil.displayGraph(Common.toQ(loopMembers).eval());
				}
			}
		}
		return problematicNode;
	}

	private void verifyLoop(Node loopHeader) {
		Node function = CommonQueries.getContainingFunction(loopHeader);
		Q loopBody = getLoopBody(loopHeader, CommonQueries.cfg(function));
		funs.add(function);
		String fName = function.getAttr(XCSG.name).toString();
		String sourceFilePath = Utils.getCSourceFilePath(loopHeader);
		String loopID = loopHeader.address().toAddressString();
		Long loopLineNumber = Utils.getLineNumber(loopHeader);
		Node problematicNode = isLoopProblematic(loopBody, loopHeader);
		if (problematicNode != null) {
			problematicLoopNum++;
			problematicFuns.add(function);
			String nodeID = problematicNode.address().toAddressString();
			Long nodeLineNumber = Utils.getLineNumber(problematicNode);
			FileWriter fw = MemoryVerificationProperties.getOutputProblematicLoopResultFileWriter();
			this.save2ProblematicLoopFile(fw, fName, loopID, loopLineNumber, nodeID, nodeLineNumber, sourceFilePath);
			if (saveGraph) {
				Path path = MemoryVerificationProperties.getProblematicLoopOutputDirectory();
				Utils.saveGraph(loopHeader, function, Common.empty(), Common.empty(), Common.empty(), path, "P",
						"Crazy");
			}
		} else {
			verifyLoop(loopHeader, function, loopBody, fName, loopID, loopLineNumber, sourceFilePath);
		}	
		
	}

	private void verifyLoop(Node loopHeader, Node function, Q loopBody, String fName, String loopID,
			Long loopLineNumber, String sourceFilePath) {

		AtlasSet<Node> callsites = loopBody.children().nodes(XCSG.CallSite).eval().nodes();
		int mallocFlag = 0;
		int freeFlag = 0;
		AtlasSet<Node> mallocEvents = new AtlasHashSet<Node>();
		AtlasSet<Node> kfreeEvents = new AtlasHashSet<Node>();
		AtlasSet<Node> callSiteEvents = new AtlasHashSet<Node>();
		String signature = "";
		for (Node callsite : callsites) {
			Node cfNode = Common.toQ(callsite).parent().eval().nodes().one();
			AtlasSet<Node> tg = invokedEdges.successors(Common.toQ(callsite)).eval().nodes();
			for (Node target : tg) {
				if (target.getAttr(XCSG.name).toString().equals(event1)) {
					signature = event1;
					mallocEvents.add(cfNode);
					mallocFlag = 1;
				} else if (target.getAttr(XCSG.name).toString().equals(event2)) {
					kfreeEvents.add(cfNode);
					freeFlag = 1;
				} else if (kmalloconly.contains(target)) {
					signature = target.getAttr(XCSG.name).toString();
					callSiteEvents.add(cfNode);
					mallocFlag = 1;
				} else if (kfreeonly.contains(target)) {
					callSiteEvents.add(cfNode);
					freeFlag = 1;
				}

			}
		}
		if (mallocFlag == 1) {
			firstEeventInsideLoopNum++;
			FileWriter fw = MemoryVerificationProperties.getOutputCFileWriter();
			this.save2LoopFile(fw, fName, loopID, loopLineNumber, sourceFilePath);
			firstEventInsideLoopContainingFunctions.add(function);
			if (saveGraph) {
				Path path = MemoryVerificationProperties.getCOutputDirectory();
				Utils.saveGraph(loopHeader, function, Common.toQ(mallocEvents), Common.toQ(kfreeEvents),
						Common.toQ(callSiteEvents), path, "C", signature);
			}

		}

		if (mallocFlag == 1 && freeFlag == 1) {
			bothEventInsideLoopNum++;
			FileWriter fw = MemoryVerificationProperties.getOutputC1FileWriter();
			this.save2LoopFile(fw, fName, loopID, loopLineNumber, sourceFilePath);
			bothEventInsideLoopContainingFunctions.add(function);
			Q cfgTargets = CommonQueries.cfg(function).children().nodes(XCSG.CallSite);
			for (Node callsite : cfgTargets.eval().nodes()) {
				Node cfNode = Common.toQ(callsite).parent().eval().nodes().one();
				if (!cfNode.hasAttr("Toolbox.loopDepth")) {
					AtlasSet<Node> tg = invokedEdges.successors(Common.toQ(callsite)).eval().nodes();
					for (Node n : tg) {
						if (n.getAttr(XCSG.name).toString().equals("kfree")) {
							kfreeEvents.add(cfNode);
							freeFlag = 1;
						} else if (kfreeonly.contains(n)) {
							callSiteEvents.add(cfNode);
							freeFlag = 1;
						}
					}
				}
			}
			if (saveGraph) {
				Path path = MemoryVerificationProperties.getC1OutputDirectory();
				Utils.saveGraph(loopHeader, function, Common.toQ(mallocEvents), Common.toQ(kfreeEvents),
						Common.toQ(callSiteEvents), path, "C1", signature);
			}
		} else if (mallocFlag == 1 && freeFlag == 0) {
			Q cfgTargets = CommonQueries.cfg(function).children().nodes(XCSG.CallSite);
			for (Node callsite : cfgTargets.eval().nodes()) {
				Node cfNode = Common.toQ(callsite).parent().eval().nodes().one();
				if (!cfNode.hasAttr("Toolbox.loopDepth")) {
					AtlasSet<Node> tg = invokedEdges.successors(Common.toQ(callsite)).eval().nodes();
					for (Node n : tg) {
						if (n.getAttr(XCSG.name).toString().equals(event2)) {
							kfreeEvents.add(cfNode);
							freeFlag = 1;
						} else if (kfreeonly.contains(n)) {
							callSiteEvents.add(cfNode);
							freeFlag = 1;
						}
					}
				}
			}
			if (freeFlag == 1) {
				firstEeventInsideLoopSecondInsideFunctionNum++;
				firstEventInsideLoopSecondEventInsideFunctionContainingFunctions.add(function);
				FileWriter fw = MemoryVerificationProperties.getOutputC2FileWriter();
				this.save2LoopFile(fw, fName, loopID, loopLineNumber, sourceFilePath);
				if (saveGraph) {
					Path path = MemoryVerificationProperties.getC2OutputDirectory();
					Utils.saveGraph(loopHeader, function, Common.toQ(mallocEvents), Common.toQ(kfreeEvents),
							Common.toQ(callSiteEvents), path, "C2", signature);
				}
			} else {
				firstEeventInsideLoopSecondNotInsideFunctionNum++;
				firstEventInsideLoopSecondEventNotInsideFunctionContainingFunctions.add(function);
				FileWriter fw = MemoryVerificationProperties.getOutputC3FileWriter();
				this.save2LoopFile(fw, fName, loopID, loopLineNumber, sourceFilePath);
				if (saveGraph) {
					Path path = MemoryVerificationProperties.getC3OutputDirectory();
					Utils.saveGraph(loopHeader, function, Common.toQ(mallocEvents), Common.toQ(kfreeEvents),
							Common.toQ(callSiteEvents), path, "C3", signature);
				}
			}
		}
	}

	private void save2LoopFile(FileWriter fw, String fName, String loopID, Long loopLineNumber, String sourceFilePath) {
		try {
			fw.write(fName + " , " + loopID + " , " + loopLineNumber + " , " + sourceFilePath + "\n");
			fw.flush();
		} catch (IOException e) {
			System.err.println("Cannot write to log file.");
		}
	}

	private void save2ProblematicLoopFile(FileWriter fw, String fName, String loopID, Long loopLineNumber,
			String nodeID, Long nodeLineNumber, String sourceFilePath) {
		try {
			fw.write(fName + " , " + loopID + " , " + loopLineNumber + " , " + nodeID + ", " + nodeLineNumber + " , "
					+ sourceFilePath + "\n");
			fw.flush();
		} catch (IOException e) {
			System.err.println("Cannot write to log file.");
		}
	}

	private Q getLoopBody(Node loopHeader, Q cfg) {
		AtlasSet<Edge> backEdges = cfg.edges(XCSG.ControlFlowBackEdge, "PCGBackEdge").eval().edges();
		AtlasSet<Node> loopMembers = Common.toQ(loopHeader).reverseOn(dominanceFrontierEdges).eval().nodes();
		Q dag = cfg.differenceEdges(Common.toQ(backEdges));
		loopMembers = Common.toQ(loopMembers).difference(dag.reverse(Common.toQ(loopHeader)))
				.union(Common.toQ(loopHeader)).eval().nodes();
		return Common.toQ(loopMembers);
	}

	private void preprocess() {
		kmalloc = CommonQueries.functions("kmalloc");
		kfree = CommonQueries.functions("kfree");
		Q kmallocCallers = callEdges.predecessors(kmalloc);
		Q kfreeCallers = callEdges.predecessors(kfree);
		kmalloconly = new AtlasHashSet<Node>();
		kfreeonly = new AtlasHashSet<Node>();
		AtlasSet<Node> mOnly = kmallocCallers.difference(kfreeCallers).eval().nodes();
		// ---start added
		AtlasSet<Node> withoutWrapper = kmallocCallers.difference(Common.toQ(mOnly)).eval().nodes();
		AtlasSet<Node> allmallocCallers = Common.toQ(withoutWrapper).union(Common.toQ(mOnly)).eval().nodes();
		// --end added
		kmalloconly.addAll(mOnly);
		kmalloconly.addAll(kmalloc.eval().nodes());
		AtlasSet<Node> fonly = kfreeCallers.difference(kmallocCallers).eval().nodes();
		kfreeonly.addAll(fonly);
		kfreeonly.addAll(kfree.eval().nodes());
		firstEventContainingFunctions.addAll(allmallocCallers);
		firstEeventInsideLoopNum = 0L;
		bothEventInsideLoopNum = 0L;
		firstEeventInsideLoopSecondInsideFunctionNum = 0L;
		firstEeventInsideLoopSecondNotInsideFunctionNum = 0L;
	}
}
