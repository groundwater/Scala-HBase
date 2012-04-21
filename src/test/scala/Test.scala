import org.specs2.mutable._

import ca.underflow.hbase._
import ca.underflow.hbase.Convert._
import org.apache.hadoop.hbase.util.Bytes

case class StringWrap(data: Data) {
    def str = Bytes.toString(data.bytes)
}

class HelloWorldSpec extends Specification {

    implicit def wrap(d: Data): StringWrap = {
        StringWrap(d)
    }
    
    "Transaction Keys" should {
        "make from implicit conversion" in {
            val key = Key("x","y","z")
            
            key.row.str must beEqualTo("x")
            key.fam.str must beEqualTo("y")
            key.col.str must beEqualTo("z")
        }
        "make from mixed values" in {
            val key = Key(Bytes.toBytes("x"),"y","z")
            
            key.row.str must beEqualTo("x")
            key.fam.str must beEqualTo("y")
            key.col.str must beEqualTo("z")
        }
        "key from single string" in {
            val k: Key = "a:b:c"
            k must not be null
        }
        
    }
    
    "Transaction values" should {
        "make from implicit conversions" in {
            Value("HI").data.str must beEqualTo("HI")
        }
    }
    
    "Mutation" should {
        "make from strings" in {
            val mut = Mutation("a","b","c","d")
            
            mut.key.row.str must beEqualTo("a")
            mut.key.fam.str must beEqualTo("b")
            mut.key.col.str must beEqualTo("c")
            mut.value.data.str must beEqualTo("d")
        }
        "make from mixed values" in {
            val b = Bytes.toBytes("b")
            val d = Bytes.toBytes("d")
            val mut = Mutation("a",b,"c",d)
            
            mut.key.row.str must beEqualTo("a")
            mut.key.fam.str must beEqualTo("b")
            mut.key.col.str must beEqualTo("c")
            mut.value.data.str must beEqualTo("d")
            
        }
        "more fun" in {
            val mut = Mutation("a:b:c","HELLO")
            mut must not be null
        }
        "Implicit using ~" in {
            val mut: Mutation = "a:b:c" ~ "d"
            mut must not be null
        }
    }
    
    "Transaction Coprocessor" should {
        "process a single mutation" in {
            val cop = new TransactionalBasic()
            cop.commit( Mutation("x","y","z","k") )
            
            cop must not be null
        }
    }
    
    "Assertions" should {
        "create a Some Assert" in {
            val some = Some( Key("a","b","c") ) 
            some.key must not be null
        }
        "create an assert from single string" in {
            val some = Some("a:b:c")
            some must not be null
        }
        "And assertions" in {
            And( Some("a:b:c"), Some("d:e:f") ) must not be null
        }
        "Or assertino" in {
            Or( Some("a:b:c"), Some("d:e:f") ) must not be null
        }
        
    }
    
    "Transactional Coprocessors" should {
        "take converted values" in {
            val convo = new TransactionalBasic()
            convo.commit("a:b:c"~"D")
            convo.commit("a:b:c"~1)
            
            convo must not be null
        }
    }
    
}







