package activitystreamer;

import activitystreamer.util.Settings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.HashSet;
import java.util.Set;

public class test {
    /*
    *   {
        "command" : "ACTIVITY_MESSAGE",
        "username" : "u1",
        "secret" : "idl4jca3v0j5uji0ts3vfbrgqj",
        "activity" : {"msg":"try AFTER A crash"}
        }
    * */
    public static void main(String[] args) {
        JSONParser parser = new JSONParser();
        JSONObject obj0 = new JSONObject();
        Set<String> set = new HashSet<>();
        //set.add("a");
        //set.add("b");
        obj0.put("set",set.toString());
        String msg = obj0.toString();
        System.out.println(msg);

        try {
            JSONObject obj = (JSONObject) parser.parse(msg);
            Set<String> set1 = Settings.stringToHashSet(obj.get("set").toString());
            System.out.println(set1 + " " + set1.size());
        } catch (ParseException e) {
            System.out.println("received parse exception: " + e);
        }

    }
}
