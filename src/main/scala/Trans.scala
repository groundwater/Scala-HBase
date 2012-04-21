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
