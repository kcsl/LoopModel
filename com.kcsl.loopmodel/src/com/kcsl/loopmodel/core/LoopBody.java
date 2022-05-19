package com.kcsl.loopmodel.core;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.c.commons.analysis.CommonQueries;
import com.kcsl.loopmodel.algorithms.DLI;

public class LoopBody {
	Q loopChildEdges = Common.empty();

	LoopBody() {
		DLI.recoverLoops();
		loopChildEdges = Common.universe().edges(XCSG.LoopChild);
	}

	public static Q getLB(Q header, Q cfg) {
		LoopBody lb = new LoopBody();
		return lb.getLoopBody(header, cfg);
	}

	public Q getLoopBody(Q header, Q cfg) {
		Q backEdges = cfg.edges("DLI.LoopBackEdge");
		// Q nestedHeaders = Common.empty();
		Q nestedLoopHeaders = getNestedLoopHeaders(header);
		// nestedLoopHeaders = nestedLoopHeaders.union(header);
		Q retainBackEdges = nestedLoopHeaders.reverseStepOn(backEdges).retainEdges();
		Q dag = cfg.differenceEdges(backEdges);
		Q newCf = dag.union(retainBackEdges);
		Q loopbody = newCf.between(header, header);
		// DisplayUtil.displayGraph(Common.toQ(loopMembers).eval());
		return loopbody;
	}

	public Q getNestedLoopHeaders(Q header) {
		Q childLoops = header;
		Q nestedHeaders = header;
		while (!CommonQueries.isEmpty(childLoops)) {
			Q nestedLoopHeaders = loopChildEdges.successors(childLoops).nodes("DLI.Loop");
			// DisplayUtil.displayGraph(nestedLoopHeaders.eval());
			nestedHeaders = nestedHeaders.union(nestedLoopHeaders);
			childLoops = nestedLoopHeaders;
		}
		return nestedHeaders;
	}

	private static boolean isLoopWithNormalExit(Node header) {
		return header.getAttr(XCSG.name).toString().matches("(for|while).*");
	}
}