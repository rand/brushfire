package com.stripe.brushfire

import com.twitter.algebird._

sealed trait Node[K, V, T]
case class SplitNode[K, V, T](val children: Seq[(K, Predicate[V], Node[K, V, T])]) extends Node[K, V, T]
case class LeafNode[K, V, T](
  val index: Int,
  target: T) extends Node[K, V, T]

case class Tree[K, V, T](root: Node[K, V, T]) {
  private def findLeaf(row: Map[K, V], start: Node[K, V, T]): Option[LeafNode[K, V, T]] = {
    start match {
      case leaf: LeafNode[K, V, T] => Some(leaf)
      case SplitNode(children) =>
        children
          .find { case (feature, predicate, _) => predicate(row.get(feature)) }
          .flatMap { case (_, _, child) => findLeaf(row, child) }
    }
  }

  def leafAt(leafIndex: Int): Option[LeafNode[K, V, T]] = leafAt(leafIndex, root)

  def leafAt(leafIndex: Int, start: Node[K, V, T]): Option[LeafNode[K, V, T]] = {
    start match {
      case leaf: LeafNode[K, V, T] => if (leaf.index == leafIndex) Some(leaf) else None
      case SplitNode(children) =>
        children
          .flatMap { case (_, _, child) => leafAt(leafIndex, child) }
          .headOption
    }
  }

  def leafIndexFor(row: Map[K, V]) = findLeaf(row, root).map { _.index }

  def targetFor(row: Map[K, V]) = findLeaf(row, root).map { _.target }

  def growByLeafIndex(fn: Int => Seq[(K, Predicate[V], T)]) = {
    var newIndex = -1
    def incrIndex = {
      newIndex += 1
      newIndex
    }

    def growFrom(start: Node[K, V, T]): Node[K, V, T] = {
      start match {
        case LeafNode(index, target) => {
          val newChildren = fn(index)
          if (newChildren.isEmpty)
            LeafNode[K, V, T](incrIndex, target)
          else
            SplitNode[K, V, T](newChildren.map {
              case (feature, predicate, target) =>
                (feature, predicate, LeafNode[K, V, T](incrIndex, target))
            })
        }
        case SplitNode(children) => SplitNode[K, V, T](children.map {
          case (feature, predicate, child) =>
            (feature, predicate, growFrom(child))
        })
      }
    }

    Tree(growFrom(root))
  }

  def updateByLeafIndex(fn: Int => Option[T]) = {
    def updateFrom(start: Node[K, V, T]): Node[K, V, T] = {
      start match {
        case LeafNode(index, target) =>
          LeafNode[K, V, T](index, fn(index).getOrElse(target))
        case SplitNode(children) => SplitNode[K, V, T](children.map {
          case (feature, predicate, child) =>
            (feature, predicate, updateFrom(child))
        })
      }
    }

    Tree(updateFrom(root))
  }
}

object Tree {
  def empty[K, V, T](t: T): Tree[K, V, T] = Tree(LeafNode[K, V, T](0, t))
}

