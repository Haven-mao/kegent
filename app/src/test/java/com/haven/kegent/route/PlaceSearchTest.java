package com.haven.kegent.route;

import org.json.JSONArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PlaceSearchTest {
    @Test public void photonGeojsonIsNormalizedWithoutSwappingLatitudeAndLongitude() throws Exception {
        List<PlaceSearch.Result> result = PlaceSearch.decode("{\"features\":[{\"geometry\":{\"coordinates\":[122.1194461,40.6867867]},\"properties\":{\"name\":\"大连理工大学(盘锦校区)\",\"city\":\"盘锦市\",\"extent\":[122.114,40.691,122.124,40.681]}}]}");
        assertEquals(1, result.size()); assertEquals(40.6867867, result.get(0).point.latitude, 1e-8);
        assertEquals(122.1194461, result.get(0).point.longitude, 1e-8); assertEquals(40.681, result.get(0).south, 0);
        assertTrue(result.get(0).address.contains("盘锦市"));
    }
    @Test public void chineseSearchResultPreservesWgs84CoordinateOrderAndExtent() throws Exception {
        List<PlaceSearch.Result> result = PlaceSearch.parse(new JSONArray("[{\"lat\":\"40.6867867\",\"lon\":\"122.1194461\",\"name\":\"大连理工大学(盘锦校区)\",\"display_name\":\"大连理工大学(盘锦校区),盘锦市\",\"boundingbox\":[\"40.6815829\",\"40.6910556\",\"122.1142296\",\"122.1241109\"]}]"));
        assertEquals(1, result.size()); assertEquals(40.6867867, result.get(0).point.latitude, 1e-8); assertEquals(122.1194461, result.get(0).point.longitude, 1e-8);
        assertEquals("大连理工大学(盘锦校区)", result.get(0).name); assertTrue(result.get(0).north > result.get(0).south);
    }
    @Test public void invalidCoordinatesAreSkippedAndInvalidExtentFallsBackToPoint() throws Exception {
        List<PlaceSearch.Result> result = PlaceSearch.parse(new JSONArray("[{\"lat\":100,\"lon\":122},{\"lat\":40,\"lon\":122,\"display_name\":\"公园,盘锦\",\"boundingbox\":[45,30,200,120]}]"));
        assertEquals(1, result.size()); assertEquals("公园", result.get(0).name); assertEquals(40, result.get(0).south, 0); assertEquals(40, result.get(0).north, 0);
    }
    @Test public void anEmptySearchIsHandled() throws Exception { assertTrue(PlaceSearch.parse(new JSONArray()).isEmpty()); }
}
