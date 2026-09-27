package CCDD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import CCDD.CcddCSVHandler.CSVTags;

/** Headless tests of the real CSV section extractor; no database is opened. */
@RunWith(Parameterized.class)
public class CcddCSVDataTest
{
    private final String searchKey;
    private final String input;
    private final String expected;

    public CcddCSVDataTest(String description, String searchKey, String input, String expected)
    {
        this.searchKey = searchKey;
        this.input = input;
        this.expected = expected;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> cases() throws Exception
    {
        List<Object[]> cases = new ArrayList<Object[]>();
        CSVTags[] tags = {CSVTags.INPUT_TYPE, CSVTags.TABLE_TYPE, CSVTags.DATA_TYPE,
                          CSVTags.MACRO, CSVTags.GROUP, CSVTags.SCRIPT_ASSOCIATION,
                          CSVTags.TELEM_SCHEDULER_DATA, CSVTags.TELEM_SCHEDULER_COMMENTS,
                          CSVTags.APP_SCHEDULER_DATA, CSVTags.APP_SCHEDULER_COMMENTS,
                          CSVTags.RESERVED_MSG_IDS, CSVTags.PROJECT_DATA_FIELD,
                          CSVTags.NAME_TYPE, CSVTags.DBU_INFO};

        for (CSVTags tag : tags)
        {
            String section = tag.getTag() + "\n\"record\",\"value\"";
            addKeys(cases, tag.name(), tag.getTag(), "header\n" + section
                    + "\n_unknown_section_\nother", section);
            cases.add(new Object[] {tag.name() + " missing", new String(tag.getTag()),
                                    "unrelated\nrows", ""});
        }

        String tables = "_name_type_\nTableA,Structure\n_description_\nfirst table"
                        + "\n_column_data_\na,uint8\n_data_field_\nfieldA,valueA"
                        + "\n_name_type_\nTableB,Structure\n_description_\nsecond table"
                        + "\n_column_data_\nb,uint16\n_data_field_\nfieldB,valueB";
        String types = "_table_type_\nTypeA,description\ncolumnA,input"
                       + "\n_table_type_data_field_\nfieldA,valueA"
                       + "\n_table_type_\nTypeB,description\ncolumnB,input"
                       + "\n_table_type_data_field_\nfieldB,valueB";
        String groups = "_group_\nGroupA,description\n_group_data_field_\nfieldA,valueA"
                        + "\n_group_\nGroupB,description\n_group_data_field_\nfieldB,valueB";
        String scheduler = "_telem_sched_comments_\ncomment"
                           + "\n_rate_info_\nstream,rate";
        String[][] composites = {{CSVTags.NAME_TYPE.getTag(), tables},
                                 {CSVTags.TABLE_TYPE.getTag(), types},
                                 {CSVTags.GROUP.getTag(), groups},
                                 {CSVTags.TELEM_SCHEDULER_COMMENTS.getTag(), scheduler}};
        for (String[] composite : composites)
        {
            for (boolean atEnd : new boolean[] {false, true})
            {
                String input = composite[1] + (atEnd ? "" : "\n_macro_\nnext,value");
                addKeys(cases, composite[0] + " composite EOF=" + atEnd,
                        composite[0], input, composite[1]);
            }
        }

        for (String key : new String[] {null, "", "_unknown_", "_MACRO_", "_macros_",
                                       CSVTags.DESCRIPTION.getTag()})
        {
            cases.add(new Object[] {"unsupported " + key, key, "_macro_\nx,y", ""});
        }
        cases.add(new Object[] {"empty CSV", new String(CSVTags.MACRO.getTag()), "", ""});
        cases.add(new Object[] {"EOF", new String(CSVTags.MACRO.getTag()),
                                "_macro_\nx,y", "_macro_\nx,y"});
        cases.add(new Object[] {"exact tag boundary", new String(CSVTags.MACRO.getTag()),
                                "_macro_extra_\nx,y", ""});
        cases.add(new Object[] {"null key with null data", null, null, ""});
        return cases;
    }

    private static void addKeys(List<Object[]> cases, String label, String tag,
                                String input, String expected) throws Exception
    {
        String copied = new String(tag);
        String read;
        try (BufferedReader reader = new BufferedReader(new StringReader(tag + "\n")))
        {
            read = reader.readLine();
        }
        assertNotSame(tag, copied);
        assertNotSame(tag, read);
        cases.add(new Object[] {label + " enum key", tag, input, expected});
        cases.add(new Object[] {label + " copied key", copied, input, expected});
        cases.add(new Object[] {label + " read key", read, input, expected});
    }

    @Test
    public void retrievesSectionsByKeyValue()
    {
        // Only unrelated application services are mocked. The constructor and
        // retrieveCSVData implementation are the production implementations.
        CcddCSVHandler handler = new CcddCSVHandler(mock(CcddMain.class), null, null);
        StringBuilder data = input == null ? null : new StringBuilder(input);
        StringBuilder actual = handler.retrieveCSVData(searchKey, data);
        assertNotNull(actual);
        assertEquals(expected, actual.toString());
        assertNotSame(data, actual);
        if (data != null)
        {
            assertEquals(input, data.toString());
            actual.append("changed result");
            assertEquals(input, data.toString());
        }
    }
}
