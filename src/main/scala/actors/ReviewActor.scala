package com.applaudostudios.fcastro.hp_project
package actors

import actors.ReviewActor._
import data.Review

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.DateTime
import akka.persistence.{PersistentActor, SnapshotOffer}

object ReviewActor {
  def props(id: String, store: ActorRef): Props = Props(
    new ReviewActor(id, store)
  )

  //Commands
  case class Create(product: Review)

  case class Update(product: Review)

  case class SetCustomer(customer: Long)

  case class SetProduct(product: String)

  //Events
  case class ReviewCreated(initial: Review) extends MySerializable

  case class ReviewUpdated(review: Review) extends MySerializable

  case class CustomerSet(id: Long) extends MySerializable

  case class ProductSet(id: String) extends MySerializable

  case class ReviewState(var opCount: Long = 0, var review: Review)
      extends MySerializable

  case object Read

  case object End

}

class ReviewActor(id: String, store: ActorRef)
    extends PersistentActor
    with ActorLogging {
  var state: ReviewState = ReviewState(review =
    Review(
      id = id,
      helpful = 0,
      votes = 0,
      vine = Some(false),
      verified = Some(false)
    )
  )

  override def receiveRecover: Receive = {
    case ReviewUpdated(updated) => applyUpdates(updated)
    case ReviewCreated(initial) => state.review = initial
    case SetCustomer(customer) =>
      state.review = state.review.copy(customer = customer)
    case SetProduct(product) =>
      state.review = state.review.copy(product = product)
    case SnapshotOffer(_, snap: ReviewState) => state = snap
  }

  override def receiveCommand: Receive = {
    case Read =>
      sender() ! state.review
    case Create(initial) =>
      persist(ReviewCreated(initial)) { createEvent =>
        state.review = createEvent.initial
        sender() ! state.review
      }
    case Update(updated) =>
      persist(ReviewUpdated(updated)) { updateEvent =>
        applyUpdates(updateEvent.review)
        sender() ! state.review
        testAndSnap()
      }
    case SetCustomer(id) =>
      persist(CustomerSet(id)) { _ =>
        state.review = state.review.copy(customer = id)
        sender() ! state.review
        testAndSnap()
      }
    case SetProduct(id) =>
      persist(ProductSet(id)) { _ =>
        log.info(s"Set product to $id")
        state.review = state.review.copy(product = id)
        sender() ! state.review
        testAndSnap()
      }
  }

  def applyUpdates(updated: Review): Unit = {
    state.review = Review(
      id = id,
      region =
        if (!updated.region.isBlank) updated.region else state.review.region,
      title = if (!updated.title.isBlank) updated.title else state.review.title,
      customer = state.review.customer,
      product = state.review.product,
      body = if (!updated.body.isBlank) updated.body else state.review.body,
      rating = if (updated.rating != 0) updated.rating else state.review.rating,
      helpful =
        if (updated.helpful >= 0) updated.helpful else state.review.helpful,
      votes = if (updated.votes >= 0) updated.votes else state.review.votes,
      date =
        if (updated.date.equals(DateTime.apply(0))) updated.date
        else state.review.date,
      vine = updated.vine match {
        case None       => state.review.vine;
        case Some(bool) => Some(bool)
      },
      verified = updated.verified match {
        case None       => state.review.verified;
        case Some(bool) => Some(bool)
      }
    )
  }

  def testAndSnap(): Unit = {
    state.opCount += 1
    if (state.opCount % 20 == 0) saveSnapshot(state)
  }

  override def persistenceId: String = s"review-actor-$id"
}