package br.com.financasdocasal
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
class ParserTest {
 private val names=listOf("Ana","João")
 @Test fun mercado(){val e=Parser.parse("gastei 50 no mercado",names)!!;assertEquals(50.0,e.amount,0.001);assertEquals("Mercado",e.category);assertEquals("Não informado",e.payer)}
 @Test fun deliveryOntem(){val e=Parser.parse("Ana pagou 120 de delivery ontem",names)!!;assertEquals("Ana",e.payer);assertEquals("Delivery",e.category);val c=java.util.Calendar.getInstance().apply{add(java.util.Calendar.DAY_OF_YEAR,-1)};assertEquals(SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.time),e.date)}
 @Test fun uberJoao(){val e=Parser.parse("João gastou 30 de uber",names)!!;assertEquals("João",e.payer);assertEquals("Transporte",e.category)}
 @Test fun noAmount(){assertNull(Parser.parse("fui ao mercado",names))}
}
