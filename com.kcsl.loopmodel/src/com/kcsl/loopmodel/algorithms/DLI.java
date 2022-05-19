package com.kcsl.loopmodel.algorithms;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.lang.Thread.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.operation.ForwardGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasNodeHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.SingletonAtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.utilities.NodeSourceCorrespondenceSorter;
import com.ensoftcorp.open.commons.xcsg.Toolbox;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;

/**
 * Uses algorithm from Wei et al. to identify loops, even irreducible ones.
 * 
 * "A New Algorithm for Identifying Loops in Decompilation". Static Analysis
 * Lecture Notes in Computer Science Volume 4634, 2007, pp 170-183
 * http://link.springer.com/chapter/10.1007%2F978-3-540-74061-2_11
 * http://www.lenx.100871.net/papers/loop-SAS.pdf
 * 
 * @author Tom Deering - initial implementation
 * @author Jon Mathews - replaced recursive logic iterative implementation
 * @author Nikhil Ranade - added loop child edges to match Atlas for Java graph schema
 * @author Ben Holland - minor refactoring, integration utils, Atlas3 migrations, XCSG schema translations
 */
public class DLI implements Runnable {
	
	public static void registerHierarchy(){
		try {
			XCSG.HIERARCHY.registerTag(CFGNode.IRREDUCIBLE_LOOP, XCSG.Loop);
			XCSG.HIERARCHY.registerTag(CFGNode.NATURAL_LOOP, XCSG.Loop);
			XCSG.HIERARCHY.registerTag(CFGEdge.LOOP_REENTRY_EDGE, XCSG.ControlFlowBackEdge);
		} catch (Exception e){
			Log.error("Unable to register decompiled loop identification tag hierarchy.", e);
		}
	}
	
	public static interface CFGNode {

		/**
		 * Tag applied to loop reentry CFG node
		 */
		@XCSG_Extension
		public static final String LOOP_REENTRY_NODE = "LOOP_REENTRY_NODE";

		/**
		 * Tag applied to irreducible loop headers
		 */
		@XCSG_Extension
		public static final String IRREDUCIBLE_LOOP = "IRREDUCIBLE_LOOP";

		/**
		 * Tag applied to natural loop headers (a LOOP_HEADER not tagged
		 * IRREDUCIBLE_LOOP).
		 */
		@XCSG_Extension
		public static final String NATURAL_LOOP = "NATURAL_LOOP";

		/**
		 * Integer attribute identifier, matches the LOOP_HEADER_ID for the
		 * innermost loop header of this node.
		 */
		@XCSG_Extension
		public static final String LOOP_MEMBER_ID = "LOOP_MEMBER_ID";

		/**
		 * Integer attribute identifier for this loop header.
		 */
		@XCSG_Extension
		public static final String LOOP_HEADER_ID = "LOOP_HEADER_ID";
		
	}

	public static interface CFGEdge {
		/**
		 * Tag for ControlFlow_Edge indicating a loop re-entry. Also called a
		 * "cross edge".
		 */
		@XCSG_Extension
		public static final String LOOP_REENTRY_EDGE = "LOOP_REENTRY_EDGE";
		
		/**
		 * Tag for interprocedural loop child edges
		 * 
		 * Note: This may move to XCSG later.
		 */
		@XCSG_Extension
		public static final String INTERPROCEDURAL_LOOP_CHILD = "INTERPROCEDURAL_LOOP_CHILD";

	}

	public static void recoverLoops() {
		recoverLoops(new NullProgressMonitor());
	}

	public static void recoverLoops(IProgressMonitor monitor) {
		_recoverLoops(monitor);
	}

	/**
	 * Identify all loop fragments, headers, re-entries, and nesting in the
	 * universe graph, applying the tags and attributes in interfaces CFGNode
	 * and CFGEdge.
	 * 
	 * NOTE: Handles both natural and irreducible loops
	 * 
	 * @return
	 */
	private static void _recoverLoops(IProgressMonitor monitor) {
		try {
			// find the work to be done
			Q u = universe();
			Graph cfContextG = resolve(null, u.edges(XCSG.ControlFlow_Edge, XCSG.ExceptionalControlFlow_Edge).eval());
			AtlasSet<Node> cfRoots = u.nodes(XCSG.controlFlowRoot).eval().nodes();
			int work = (int) cfRoots.size();
			ArrayList<Node> rootList = new ArrayList<Node>(work);
			for (Node root : cfRoots){
				rootList.add(root);
			}

			monitor.beginTask("Identify Local Loops", rootList.size());

			// assign the work to worker threads
			int procs = Runtime.getRuntime().availableProcessors();
			Thread[] threads = new Thread[procs];
			int workPerProc = work / procs;
			int remainder = work % procs;
			for (int i = 0; i < procs; ++i) {
				int firstInclusive = workPerProc * i + Math.min(remainder, i);
				int lastExclusive = firstInclusive + workPerProc + (i < remainder ? 1 : 0);
				threads[i] = new Thread(new DLI(monitor, cfContextG, rootList.subList(firstInclusive, lastExclusive)));
				threads[i].start();
			}

			// wait for worker threads to finish
			int waitIndex = 0;
			while (waitIndex < threads.length) {
				if (!State.TERMINATED.equals(threads[waitIndex].getState())) {
					try {
						threads[waitIndex].join();
					} catch (InterruptedException e) {
						Log.warning("Caught thread interruption exception", e);
					}
				} else {
					waitIndex++;
				}
			}
		} finally {
			monitor.done();
		}
	}

	private AtlasSet<Node> traversed, reentryNodes, irreducible;
	private AtlasSet<Edge> reentryEdges, loopbacks;
	private Graph cfContextG;

	/** The node's position in the DFSP (Depth-first search path) */
	private Map<Node, Integer> dfsp;
	private Map<Node, Node> innermostLoopHeaders;
	private List<Node> cfRoots;
	private static int idGenerator;
	private static Object idGeneratorLock = new Object();
	private IProgressMonitor monitor;

	private DLI(IProgressMonitor monitor, Graph cfContextG, List<Node> cfRoots) {
		this.monitor = monitor;
		this.cfContextG = cfContextG;
		this.cfRoots = cfRoots;
		traversed = new AtlasHashSet<Node>();
		reentryNodes = new AtlasHashSet<Node>();
		reentryEdges = new AtlasHashSet<Edge>();
		irreducible = new AtlasHashSet<Node>();
		loopbacks = new AtlasHashSet<Edge>();
		dfsp = new HashMap<Node, Integer>();
		innermostLoopHeaders = new HashMap<Node, Node>();
	}

	@Override
	public void run() {
		// compute individually on a per-function basis
		for (Node root : cfRoots) {
			try {
				// clear data from previous function
				reentryNodes.clear();
				reentryEdges.clear();
				irreducible.clear();
				traversed.clear();
				innermostLoopHeaders.clear();
				loopbacks.clear();
				dfsp.clear();

				for (Node node : new ForwardGraph(cfContextG, new SingletonAtlasSet<Node>(root)).nodes()) {
					dfsp.put(node, 0);
				}

				// run loop identification algorithm
				
				// a recursive strategy may overflow the call stack in some cases
				// so not using the loopDFSRecursive(root, 1) implementation
				// better to use an equivalent iterative strategy
				loopDFSIterative(root, 1); 

				// modify universe graph
				Collection<Node> loopHeaders = innermostLoopHeaders.values();
				
				ArrayList<Node> sortedLoopHeaders = new ArrayList<Node>(loopHeaders.size());
				for(Node loopHeader : loopHeaders){
					sortedLoopHeaders.add(loopHeader);
				}
				Collections.sort(sortedLoopHeaders, new NodeSourceCorrespondenceSorter());

				Map<Node, String> loopHeaderToID = new HashMap<Node, String>(loopHeaders.size());

				synchronized (idGeneratorLock) {
					for (Node loopHeader : sortedLoopHeaders) {
						int id = idGenerator++;
						loopHeaderToID.put(loopHeader, Integer.toString(id));
						loopHeader.tag("DLI.Loop");

						loopHeader.putAttr(CFGNode.LOOP_HEADER_ID, Integer.toString(id));
						if (irreducible.contains(loopHeader)) {
							loopHeader.tag(CFGNode.IRREDUCIBLE_LOOP);
						} else {
							loopHeader.tag(CFGNode.NATURAL_LOOP);
						}
					}
				}
				
				// Temporary fix to make Loop Catalog work with source code
				// TODO: A version needs to be created for Java source code
				for (Node cfgNode : innermostLoopHeaders.keySet()) {
					Node loopHeader = innermostLoopHeaders.get(cfgNode);
					cfgNode.putAttr(CFGNode.LOOP_MEMBER_ID, loopHeaderToID.get(loopHeader));
					if(loopHeader.taggedWith(XCSG.Language.C)) {
						if(universe().edges(XCSG.LoopChild).betweenStep(Common.toQ(loopHeader), Common.toQ(cfgNode)).eval().edges().isEmpty()) {
							Edge edge = Graph.U.createEdge(loopHeader, cfgNode);
							edge.tag(XCSG.LoopChild);
						}					
					}
					if(loopHeader.taggedWith(XCSG.Language.Jimple)) {
						Edge edge = Graph.U.createEdge(loopHeader, cfgNode);
						edge.tag(XCSG.LoopChild);
					}
				}

				for (Node reentryNode : reentryNodes) {
					reentryNode.tag(CFGNode.LOOP_REENTRY_NODE);
				}

				for (Edge reentryEdge : reentryEdges) {
					reentryEdge.tag(CFGEdge.LOOP_REENTRY_EDGE);
				}

				for (Edge loopbackEdge : loopbacks) {
					loopbackEdge.tag("DLI.LoopBackEdge");
				}
				
				recordLoopDepth(sortedLoopHeaders);
				
			} catch (Throwable t) {
				Log.error("Problem in loop analyzer thread for CFG root:\n" + root, t);
			}

			if (monitor.isCanceled()){
				return;
			}
			synchronized (monitor) {
				monitor.worked(1);
			}
		}
		System.out.println("BackEdges: "+ loopbacks.size());
	}

	private void recordLoopDepth(ArrayList<Node> loopHeaders) {
		// mark loopDepth; loopDepth starts at 1 for outermost Loops
		// and children are at the same depth as their loop headers
		// (unless the child is a loop header in which case the depth is +1)
		
		AtlasSet<Node> level1Headers = new AtlasNodeHashSet();
		for (Node loopHeader : loopHeaders) {
			if (!loopHeader.hasAttr(CFGNode.LOOP_MEMBER_ID)) {
				level1Headers.add(loopHeader);
			}
		}
		AtlasSet<Node> processedLoops = new AtlasHashSet<Node>();
		Stack<LoopLevel> loopLevels = new Stack<LoopLevel>();
		for (Node loopHeader : level1Headers) {
			loopLevels.push(new LoopLevel(loopHeader, 1));
		}
		while(!loopLevels.isEmpty()) {
			LoopLevel loopLevel = loopLevels.pop();
			Node loopHeader = loopLevel.getLoopHeader();
			int depth = loopLevel.getDepth();
			loopHeader.putAttr(Toolbox.loopDepth, depth);
			processedLoops.add(loopHeader);
			AtlasSet<Edge> loopChildren = loopHeader.out(XCSG.LoopChild);
			for (Edge loopChild : loopChildren) {
				Node member = loopChild.to();
				if (member.taggedWith(XCSG.Loop)) {
					if(!processedLoops.contains(member)) {
						// this is to prevent infinite traversals
						loopLevels.push(new LoopLevel(member, (depth+1)));
					}
				} else {
					member.putAttr(Toolbox.loopDepth, depth);
				}
			}
		}
	}
	
	private static class LoopLevel {
		private int depth;
		private Node loopHeader;
		public LoopLevel(Node loopHeader, int depth) {
			this.loopHeader = loopHeader;
			this.depth = depth;
		}
		public int getDepth() {
			return depth;
		}
		public Node getLoopHeader() {
			return loopHeader;
		}
	}

	/**
	 * Recursively traverse the current node, returning its innermost loop
	 * header
	 * 
	 * @param b0
	 * @param position
	 * @return
	 */
	@SuppressWarnings("unused")
	private void loopDFSRecursive(Node b0, int position) {
		traversed.add(b0);
		dfsp.put(b0, position);

		for (Edge cfgEdge : cfContextG.edges(b0, NodeDirection.OUT)) {
			Node b = cfgEdge.to();

			if (!traversed.contains(b)) {
				// Paper Case A
				// new
				loopDFSRecursive(b, position + 1);
				Node nh = innermostLoopHeaders.get(b);
				tag_lhead(b0, nh);
			} else {
				if (dfsp.get(b) > 0) {
					// Paper Case B
					// Mark b as a loop header
					loopbacks.add(cfgEdge);
					tag_lhead(b0, b);
				} else {
					Node h = innermostLoopHeaders.get(b);
					if (h == null) {
						// Paper Case C
						// do nothing
						continue;
					}

					if (dfsp.get(h) > 0) {
						// Paper Case D
						// h in DFSP(b0)
						tag_lhead(b0, h);
					} else {
						// Paper Case E
						// h not in DFSP(b0)
						reentryNodes.add(b);
						reentryEdges.add(cfgEdge);
						irreducible.add(h);

						while ((h = innermostLoopHeaders.get(h)) != null) {
							if (dfsp.get(h) > 0) {
								tag_lhead(b0, h);
								break;
							}
							irreducible.add(h);
						}
					}
				}
			}
		}

		dfsp.put(b0, 0);
	}

	private void tag_lhead(Node b, Node h) {
		if (h == null || h.equals(b)){
			return;
		}
		
		Node cur1 = b;
		Node cur2 = h;

		Node ih;
		while ((ih = innermostLoopHeaders.get(cur1)) != null) {
			if (ih.equals(cur2)){
				return;
			}
			if (dfsp.get(ih) < dfsp.get(cur2)) {
				innermostLoopHeaders.put(cur1, cur2);
				cur1 = cur2;
				cur2 = ih;
			} else {
				cur1 = ih;
			}
		}
		innermostLoopHeaders.put(cur1, cur2);
	}

	private Deque<Frame> stack = new ArrayDeque<Frame>();

	private static class Frame {
		int programCounter = 0;
		Node b = null;
		Node b0 = null;
		int position = 0;
		Iterator<Edge> iterator = null;
	}

	static private final int ENTER = 0;
	static private final int EACH_CFG_EDGE = 1;
	static private final int POP = 2;

	/**
	 * Iterative implementation, equivalent to loopDFSRecursive()
	 * 
	 * @param b0
	 * @param position
	 * @return
	 */
	private void loopDFSIterative(Node _b0, int _position) {
		stack.clear();

		Frame f = new Frame();
		f.b0 = _b0;
		f.position = _position;
		f.programCounter = ENTER;

		stack.push(f);

		stack: while (!stack.isEmpty()) {
			f = stack.peek();

			switch (f.programCounter) {
				case POP: {
					Node nh = innermostLoopHeaders.get(f.b);
					tag_lhead(f.b0, nh);
					f.programCounter = EACH_CFG_EDGE;
					continue stack;
				}
				case ENTER:
					traversed.add(f.b0);
					dfsp.put(f.b0, f.position);
					f.iterator = cfContextG.edges(f.b0, NodeDirection.OUT).iterator();
					// FALL THROUGH
				case EACH_CFG_EDGE:
					while (f.iterator.hasNext()) {
						Edge cfgEdge = f.iterator.next();
						f.b = cfgEdge.to();
						if (!traversed.contains(f.b)) {
							// Paper Case A
							// new
							// BEGIN CONVERTED TO ITERATIVE
							// RECURSE: loopDFS(b, position + 1);
	
							f.programCounter = POP;
	
							Frame f2 = new Frame();
							f2.b0 = f.b;
							f2.position = f.position + 1;
							f2.programCounter = ENTER;
	
							stack.push(f2);
							continue stack;
	
							// case POP:
							// Node nh = innermostLoopHeaders.get(b);
							// tag_lhead(b0, nh);
	
							// END CONVERTED TO ITERATIVE
						} else {
							if (dfsp.get(f.b) > 0) {
								// Paper Case B
								// Mark b as a loop header
								loopbacks.add(cfgEdge);
								tag_lhead(f.b0, f.b);
							} else {
								Node h = innermostLoopHeaders.get(f.b);
								if (h == null) {
									// Paper Case C
									// do nothing
									continue;
								}
								
								if (dfsp.get(h) > 0) {
									// Paper Case D
									// h in DFSP(b0)
									tag_lhead(f.b0, h);
								} else {
									// Paper Case E
									// h not in DFSP(b0)
									reentryNodes.add(f.b);
									reentryEdges.add(cfgEdge);
									irreducible.add(h);
	
									while ((h = innermostLoopHeaders.get(h)) != null) {
										if (dfsp.get(h) > 0) {
											tag_lhead(f.b0, h);
											break;
										}
										irreducible.add(h);
									}
								}
							}
						}
					}
					
					dfsp.put(f.b0, 0);
					stack.pop();
			}
		}
	}

}