package com.kenshoo.freemarker.util;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import freemarker.template.utility.DateUtil;

public class DataModelParserTest {

    @Test
    public void testEmpty() throws DataModelParsingException {
        assertTrue(DataModelParser.parse("").isEmpty());
        assertTrue(DataModelParser.parse(" \n ").isEmpty());
    }

    @Test
    public void testSingleAssignment() throws DataModelParsingException {
        assertEquals(ImmutableMap.of("n", "v"), DataModelParser.parse("n=v"));
        assertEquals(ImmutableMap.of("n", "v"), DataModelParser.parse("\n\n\tn\t= v"));
        assertEquals(ImmutableMap.of("longerN", "longer v"), DataModelParser.parse("longerN=longer v"));
        assertEquals(ImmutableMap.of("a:b.c-d$@", "foo bar\nbaaz"), DataModelParser.parse("a:b.c-d$@ = foo bar\nbaaz"));
    }

    @Test
    public void testNotBlankButHasNoAssignment() {
        try {
            DataModelParser.parse("x");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("must start with an assignment"));
        }
    }

    @Test
    public void testNoLinebreakBeforeEquals() {
        try {
            DataModelParser.parse("x\n=y");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("must start with an assignment"));
        }
    }

    @Test
    public void testMultipleAssignments() throws DataModelParsingException {
        assertEquals(ImmutableMap.of("n1", "v1", "n2", "v2", "n3", "v3"),
                DataModelParser.parse("n1=v1\nn2=v2\nn3=v3"));
        assertEquals(ImmutableMap.of("n1", "v1", "n2", "v2", "n3", "v3"),
                DataModelParser.parse(" n1 = v1 \r\n\r\n\tn2=v2\nn3 = v3\n\n"));
        assertEquals(ImmutableMap.of("n1", "=\n=v", "n2", "l1\nl2\n\nl3", "n3", "v3"),
                DataModelParser.parse("n1==\n=v \n n2=l1\nl2\n\nl3\nn3=v3"));
    }

    @Test
    public void testStrings() throws DataModelParsingException {
        assertEquals(
                ImmutableMap.of(
                    "a", "C:\\x",
                    "b", "foo\nbar",
                    "c", "foo\t\"bar\"",
                    "d", "foo\t\"bar\"",
                    "e", "Foo's"
                ),
                DataModelParser.parse(
                        "a=C:\\x\n"
                        + "b=foo\nbar\n"
                        + "c=foo\t\"bar\"\n"
                        + "d=\"foo\\t\\\"bar\\\"\"\n"
                        + "e=\"Foo's\""));
        try {
            DataModelParser.parse("a=\"foo");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("quoted"));
        }
        try {
            DataModelParser.parse("a='foo'");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("quoted"));
        }
        try {
            DataModelParser.parse("a=\"\\x\"");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("escape"));
        }
    }
    
    @Test
    public void testBasicNumbers() throws DataModelParsingException {
        assertEquals(
                ImmutableMap.of(
                    "a", BigDecimal.valueOf(1),
                    "b", BigDecimal.valueOf(1.5),
                    "c", BigDecimal.valueOf(-1.5),
                    "d", BigDecimal.valueOf(1.5),
                    "e", BigDecimal.valueOf(-0.125)
                ),
                DataModelParser.parse("a=1\nb=1.5\nc=-1.5\nd=+1.5\ne=-12.5e-2"));
        try {
            DataModelParser.parse("a=1,5");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("Malformed number"));
            assertThat(e.getMessage(), not(containsString("ISO")));
        }
    }
    
    @Test
    public void testSpecialNumbers() throws DataModelParsingException {
    assertEquals(
            ImmutableMap.of(
                "a", Double.NaN,
                "b", Double.POSITIVE_INFINITY,
                "c", Double.NEGATIVE_INFINITY,
                "d", Double.POSITIVE_INFINITY
            ),
            DataModelParser.parse("a=NaN\nb=Infinity\nc=-Infinity\nd=+Infinity"));
    }

    @Test
    public void testBooleans() throws DataModelParsingException {
        assertEquals(ImmutableMap.of("a", true, "b", false), DataModelParser.parse("a=true\nb=false"));
        try {
            DataModelParser.parse("a=True");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("true"));
        }
    }
    
    @Test
    public void testTemporals() throws DataModelParsingException {
        final Map<String, Object> dm = DataModelParser.parse("a=2014-02-12T01:02:03Z\nb=2014-02-12\nc=01:02:03Z");
        
        final GregorianCalendar cal = new GregorianCalendar(DateUtil.UTC);
        cal.clear();
        
        cal.set(2014, 1, 12, 1, 2, 3);
        Timestamp a = new Timestamp(cal.getTimeInMillis());
        assertThat(dm.get("a"), instanceOf(Timestamp.class));
        assertEquals(dm.get("a"), a);
        
        cal.set(2014, 1, 12, 0, 0, 0);
        java.sql.Date b = new java.sql.Date(cal.getTimeInMillis());
        assertThat(dm.get("b"), instanceOf(java.sql.Date.class));
        assertEquals(dm.get("b"), b);
        
        cal.set(1970, 0, 1, 1, 2, 3);
        Time c = new Time(cal.getTimeInMillis());
        assertThat(dm.get("c"), instanceOf(Time.class));
        assertEquals(dm.get("c"), c);
        
        try {
            DataModelParser.parse("a=2012T123");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("ISO 8601 date-time"));
        }
        try {
            DataModelParser.parse("a=2012-0102");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("ISO 8601 date"));
        }
        try {
            DataModelParser.parse("a=25:00");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("ISO 8601 time"));
        }
    }
    
    @Test
    public void testMaps() throws DataModelParsingException {
        final Object map = DataModelParser.parse(
                "n = {\n"
                + "\t\"a\": 1,\n"
                + "\t\"b\": 2\n"
                + "}")
                .get("n");
        assertEquals(ImmutableMap.of("a", 1, "b", 2), map);
        assertThat(map, instanceOf(LinkedHashMap.class));
        try {
            DataModelParser.parse("n={1:2}");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("JSON"));
        }
    }    
    
    @Test
    public void testLists() throws DataModelParsingException {
        final Object list = DataModelParser.parse("n=[1, 2]").get("n");
        assertEquals(ImmutableList.of(1, 2), list);
        assertThat(list, instanceOf(List.class));
        try {
            DataModelParser.parse("n=[");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("JSON"));
        }
    }

    @Test
    public void testXML() throws DataModelParsingException {
        final Object doc = DataModelParser.parse("n=<e xmlns='foo:/bar' a='123'>text</e>").get("n");
        assertThat(doc, instanceOf(Document.class));
        final Node firstChild = ((Document) doc).getFirstChild();
        assertEquals("e", firstChild.getNodeName());
        assertEquals("foo:/bar", firstChild.getNamespaceURI());
        
        try {
            DataModelParser.parse("n=<ns:e />");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("XML"));
        }
    }
    
    @Test
    public void testNull() throws DataModelParsingException {
        assertNull(DataModelParser.parse("n=null").get("n"));
        try {
            DataModelParser.parse("a=NULL");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("null"));
        }
    }

    @Test
    public void testEmptyValue() throws DataModelParsingException {
        try {
            DataModelParser.parse("n=");
            fail();
        } catch (DataModelParsingException e) {
            assertThat(e.getMessage(), containsString("Empty"));
        }
        
        assertEquals("", DataModelParser.parse("n=\"\"").get("n"));
    }
    
}
