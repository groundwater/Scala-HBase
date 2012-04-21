/** This builds a transactional layer into HBase via coprocessors.
  *
  * Transactions can be performed by sending a group of mutations,
  * to a coprocessor. The coprocessor modifies the table using a
  * special mutation protocol that ensures anyone using the same
  * protocol will only view atomic commits.
  *
  * Querying the table directly does not guaruntee atomicity. You
  * _must_ query using the provided protocol.
  *
  * The protocol guaruntees that, should a crash occur, partial
  * transactions can be recovered.
  *
  * The goal of this protocol is _not_ performance. It is an extra
  * layer of guaruntees should they be required.
  *
  * ## Mutation Protocol ##
  *
  * The mutation protocol is as follows:
  *
  * 1. The entire transaction is pre-written
  *
  * 	The transaction object is encoded as a byte stream and recorded
  * 	in a special row `transaction.<timestamp>.uid`. Column `buffer`
  * 	encodes the transaction object. The column `status` encodes the
  *    global status of the transaction.
  *
  *    Set status to `0-WRITTEN`
  *
  * 2. Lock all keys listed by mutations or assertions
  *
  * 	Keys are locked by a checkAndPut to a special lock column.
  * 	Given any key `x` the lock column is `x_lock`. 
  *    Any value means the column is locked. The tag value should be 
  *    the uid of the transactions row. Transactions must respect existing
  *    locks and either wait or abort in such a case.
  *
  *    Set the global status to `1-LOCKED`
  *
  * 3. Run all assertions
  *
  * 	Remember that any asserted cells must be locked in the previous
  * 	stage. Assertions that fail must abort the transaction.
  *
  * 	Set the global status to `2-ASSERTED`
  *
  * 4. Mutate values
  *
  * 	By now, if the transaction should halt due to a crash, recovery
  * 	software should complete the transaction during recovery.
  *
  * 	Since HBase never over-writes old values, data is never really lost
  * 	except during a compactions. Just for safety, tag the old version 
  * 	by putting its timestamp into `x_tag`.
  * 	
  * 	Put the new value.
  * 	
  * 	Once all values are inserted, set the global status to `3-PUT`
  * 
  * 5. Cleanup
  * 	
  * 	The transaction is done, but the safety measures must be cleaned
  * 	up gracefully.
  * 	
  * 	Clear all `x_tag` values then set the global status to `4-CLEANING`.
  * 	
  * 6. Unlock
  * 	
  * 	Unlock all the cells. An unloced cell sboudl be immediately available  
  * 	for another transaction, even before the rest of the cells are unlocked.
  * 	
  * 	After all tags are unlocked, set the global status to `5-COMPLETE`
  *
  *	Cleanup of transaction rows can be done at the discression of the administrators.
  * 
  * ## Discussion ##
  * 
  * I'm not sure if the `x_tag` is necessary. Certainly HBase makes it easier
  * to recover from transactions by never deleting old data. 
  * 
  * I'd like to point out again that this is not intended to be a high-performance
  * addition to HBase. Rather, should you build 90% of your application in HBase
  * then decide a small piece needs some transactional security, this is an easy
  * option to throw in the mix. Don't use it unless it's necessary.
  * 
  * I think any good implementation will require a `fsck` that can be run 
  * post crash. That should be added into the coprocessor at some point.
  * 
  */
package ca.underflow.hbase

import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.coprocessor._
import org.apache.hadoop.hbase.ipc._
import org.apache.hadoop.hbase.util._

import com.google.protobuf.Message

trait KeyBuilder {
    def key: Key
    def ~ [T <% Data](t:T): Mutation = {
        Mutation(key,t)
    }
    implicit def toKey: Key = key
}

object Convert {
    implicit def keyBuilderFromString(s: String) : KeyBuilder = {
        new KeyBuilder(){    
            val key: Key = s
        }
    }    
}

trait Data {
    def bytes: Array[Byte]
}

object Data {
    implicit def fromString(string: String): Data = {
        new Data() {
            val bytes = Bytes.toBytes(string)
        }
    }
    implicit def fromBytes(_bytes: Array[Byte]): Data = {
        new Data() {
            val bytes = _bytes
        }
    }
    implicit def fromInt(int: Int): Data = {
        new Data(){
            val bytes = Bytes.toBytes(int)
        }
    }
    implicit def fromMessage(message: Message): Data = {
        new Data(){
            val bytes = message.toByteArray()
        }
    }
}

case class Key(
        row: Data,
        fam: Data,
        col: Data){
}

object Key {
    implicit def keyFromString(s: String): Key = {
        val args = s.split(":")
        Key(args(0),args(1),args(2))
    }
}

case class Value(data: Data)

object Value {
    implicit def fromAny[T <% Data](any: T) : Value = {
        Value(any)
    }
}

case class Mutation(key: Key, value: Value)

object Mutation {
    def apply(row: Data, fam: Data, col: Data, value: Data): Mutation = {
        Mutation( Key(row, fam, col), Value(value) )
    }
    implicit def toList(mutation: Mutation) : List[Mutation]= {
        List(mutation)
    }
}

// Assertions verify existing data pre-commit.
// Failed assertions *must* abort the transaction.
trait Assertion {
    
}

object Assertion {
    implicit def toList(assertion: Assertion) : List[Assertion]= {
        List(assertion)
    }
}

case class Exist(key: Key, value: Value) extends Assertion
case class Some(key: Key) extends Assertion
case class GreaterThan(key: Key, value: Value) extends Assertion
case class Not(assertion: Assertion) extends Assertion
case class And(left: Assertion, right: Assertion) extends Assertion
case class Or(left: Assertion, right: Assertion) extends Assertion

// RPC Interface to Coprocessor
// Keep it simple :)
trait Transactional extends CoprocessorProtocol {

    // The coprocessor commits a set of mutations atomically
    def commit(mutations: Iterable[Mutation])
    def commit(mutations: Iterable[Mutation], assertions: Iterable[Assertion])
}

// Basic Coprocessor
class TransactionalBasic
        extends BaseEndpointCoprocessor
        with CoprocessorProtocol {
            
    def commit(mutations: Iterable[Mutation]) {}
    def commit(mutations: Iterable[Mutation], assertions: Iterable[Assertion]) {}

}
