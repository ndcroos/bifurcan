package io.lacuna.bifurcan;

import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * @author ztellman
 */
public class DirectedAcyclicGraph<V, E> implements IGraph<V, E> {

  public static class CycleException extends IllegalArgumentException {
    public CycleException(String message) {
      super(message);
    }
  }

  private DirectedGraph<V, E> graph;
  private Set<V> top;
  private Set<V> bottom;

  private DirectedAcyclicGraph(DirectedGraph<V, E> graph, Set<V> top, Set<V> bottom) {
    this.graph = graph;
    this.top = top;
    this.bottom = bottom;
  }

  public DirectedAcyclicGraph() {
    this(new DirectedGraph<>(), new Set<>(), new Set<>());
  }

  /**
   * @return a directed acyclic graph equivalent to {@code graph}
   * @throws CycleException if {@code graph} contains a cycle
   */
  public static <V, E> DirectedAcyclicGraph<V, E> from(DirectedGraph<V, E> graph) {
    if (Graphs.stronglyConnectedComponents(graph).size() > 0) {
      throw new CycleException("graph contains a cycle");
    }
    return new DirectedAcyclicGraph<>(
      graph,
      graph.vertices().stream().filter(v -> graph.in(v).size() == 0).collect(Sets.collector()),
      graph.vertices().stream().filter(v -> graph.out(v).size() == 0).collect(Sets.collector()));
  }

  public Set<V> top() {
    return top;
  }

  public Set<V> bottom() {
    return bottom;
  }

  @Override
  public Set<V> vertices() {
    return graph.vertices();
  }

  @Override
  public Iterator<IEdge<V, E>> edges() {
    return graph.edges();
  }

  @Override
  public E edge(V src, V dst) {
    return graph.edge(src, dst);
  }

  @Override
  public Set<V> in(V vertex) {
    return graph.in(vertex);
  }

  @Override
  public Set<V> out(V vertex) {
    return graph.out(vertex);
  }

  /**
   * @param from  the source of the edge
   * @param to    the destination of the edge
   * @param edge  the value of the edge
   * @param merge the merge function for the edge values, if an edge already exists
   * @return a graph containing the new edge
   * @throws CycleException if the new edge creates a cycle
   */
  @Override
  public DirectedAcyclicGraph<V, E> link(V from, V to, E edge, BinaryOperator<E> merge) {

    if (vertices().contains(from) && vertices().contains(to) && createsCycle(from, to)) {
      throw new CycleException("new edge creates a cycle");
    }

    DirectedGraph<V, E> graphPrime = graph.link(from, to, edge, merge);
    Set<V> topPrime = top.remove(to);
    Set<V> bottomPrime = bottom.remove(from);

    if (isLinear()) {
      graph = graphPrime;
      top = topPrime;
      bottom = bottomPrime;
      return this;
    } else {
      return new DirectedAcyclicGraph<>(graphPrime, topPrime, bottomPrime);
    }
  }

  @Override
  public DirectedAcyclicGraph<V, E> unlink(V from, V to) {

    DirectedGraph<V, E> graphPrime = graph.unlink(from, to);
    Set<V> topPrime = graph.in(to).size() == 1 ? top.add(to) : top;
    Set<V> bottomPrime = graph.out(from).size() == 1 ? bottom.add(from) : bottom;

    if (isLinear() || graph == graphPrime) {
      graph = graphPrime;
      top = topPrime;
      bottom = bottomPrime;
      return this;
    } else {
      return new DirectedAcyclicGraph<>(graphPrime, topPrime, bottomPrime);
    }
  }

  @Override
  public DirectedAcyclicGraph<V, E> merge(IGraph<V, E> graph, BinaryOperator<E> merge) {
    return from(this.graph.merge(graph, merge));
  }

  @Override
  public DirectedAcyclicGraph<V, E> select(ISet<V> vertices) {
    return new DirectedAcyclicGraph<>(
      graph.select(vertices),
      top.intersection(vertices),
      bottom.intersection(vertices));
  }

  @Override
  public DirectedAcyclicGraph<V, E> add(V vertex) {
    if (graph.vertices().contains(vertex)) {
      return this;
    } else {

      DirectedGraph<V, E> graphPrime = graph.add(vertex);
      Set<V> topPrime = top.add(vertex);
      Set<V> bottomPrime = bottom.add(vertex);

      if (isLinear()) {
        graph = graphPrime;
        top = topPrime;
        bottom = bottomPrime;
        return this;
      } else {
        return new DirectedAcyclicGraph<>(graphPrime, topPrime, bottomPrime);
      }
    }
  }

  @Override
  public DirectedAcyclicGraph<V, E> remove(V vertex) {
    if (graph.vertices().contains(vertex)) {
      Set<V> topPrime = top.union(graph.out(vertex).stream().filter(v -> graph.in(v).size() == 1).collect(Sets.collector()));
      Set<V> bottomPrime = bottom.union(graph.in(vertex).stream().filter(v -> graph.out(v).size() == 1).collect(Sets.collector()));
      DirectedGraph<V, E> graphPrime = graph.remove(vertex);

      if (isLinear()) {
        graph = graphPrime;
        top = topPrime;
        bottom = bottomPrime;
        return this;
      } else {
        return new DirectedAcyclicGraph<>(graphPrime, topPrime, bottomPrime);
      }
    } else {
      return this;
    }
  }

  @Override
  public DirectedAcyclicGraph<V, E> forked() {
    return graph.isLinear() ? new DirectedAcyclicGraph<>(graph.forked(), top.forked(), bottom.forked()) : this;
  }

  @Override
  public DirectedAcyclicGraph<V, E> linear() {
    return graph.isLinear() ? this : new DirectedAcyclicGraph<>(graph.linear(), top.linear(), bottom.linear());
  }

  @Override
  public boolean isLinear() {
    return graph.isLinear();
  }

  @Override
  public boolean isDirected() {
    return true;
  }

  @Override
  public <U> DirectedAcyclicGraph<V, U> mapEdges(Function<IEdge<V, E>, U> f) {
    return new DirectedAcyclicGraph<>(graph.mapEdges(f), top, bottom);
  }

  @Override
  public DirectedAcyclicGraph<V, E> transpose() {
    return new DirectedAcyclicGraph<>(graph.transpose(), bottom, top);
  }

  @Override
  public ToIntFunction<V> vertexHash() {
    return graph.vertexHash();
  }

  @Override
  public BiPredicate<V, V> vertexEquality() {
    return graph.vertexEquality();
  }

  @Override
  public int hashCode() {
    return graph.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return graph.equals(obj);
  }

  @Override
  public String toString() {
    return graph.toString();
  }

  ///

  private boolean createsCycle(V from, V to) {
    Iterator<V> upstreamIterator = Graphs.bfsVertices(LinearList.of(from), this::in);
    Iterator<V> downstreamIterator = Graphs.bfsVertices(LinearList.of(to), this::out);

    if (!upstreamIterator.hasNext() || !downstreamIterator.hasNext()) {
      return false;
    }

    LinearSet<V> upstream = new LinearSet<V>(vertexHash(), vertexEquality());
    LinearSet<V> downstream = new LinearSet<V>(vertexHash(), vertexEquality());

    while (upstreamIterator.hasNext() && downstreamIterator.hasNext()) {
      V a = upstreamIterator.next();
      if (downstream.contains(a)) {
        return true;
      }
      upstream.add(a);

      V b = downstreamIterator.next();
      if (upstream.contains(b)) {
        return true;
      }
      downstream.add(b);
    }

    return false;
  }
}
