package helpers;

import java.util.HashMap;
import java.util.Map;

import domain.BidResult;
import domain.Category;
import domain.Summary;
import domain.Video;
import domain.viewer.Viewer;
import domain.viewer.ViewerAge;
import domain.viewer.ViewerGender;
import domain.viewer.ViewerInterests;
import domain.viewer.ViewerSubscribed;

public class InputParser {

    public InputParser() {
    }

    public static DataParserResult readData(String data) {
        if (data == null) {
            return null;
        }

        String trimmedData = data.trim();
        if (trimmedData.isEmpty()) {
            return null;
        }

        String[] dataList = trimmedData.split(",");
        Map<String, String> fieldMap = toFieldMap(dataList);
        Video videoData = readVideoData(fieldMap);
        Viewer viewerData = readViewerData(fieldMap);

        return new DataParserResult(videoData, viewerData);
    }

    private static Video readVideoData(Map<String, String> fieldMap) {

        Category videoCategory = null;
        int viewCount = -1;
        int commentCount = -1;

        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            if (!entry.getKey().startsWith("video")) {
                continue;
            }
            String field = entry.getKey().split("\\.")[1];
            switch (field) {
                case("category"): {
                    videoCategory = parseVideoCategory(entry.getValue());
                    break;
                }
                case("viewCount"): {
                    viewCount = Integer.parseInt(entry.getValue());
                    break;
                }
                case("commentCount"): {
                    commentCount = Integer.parseInt(entry.getValue());
                    break;
                }
            }
        }
        if (viewCount == -1) {
            System.err.println("Error parsing viewCount");
        }
        if (commentCount == -1) {
            System.err.println("Error parsing commentCount");
        }

        return new Video(viewCount, videoCategory, commentCount);
    }

    private static Viewer readViewerData(Map<String, String> fieldMap) {

        ViewerAge viewerAge = null;
        ViewerGender viewerGender = null;
        ViewerInterests viewerInterests = null;
        ViewerSubscribed viewerSubscribed = null;

        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            if (!entry.getKey().startsWith("viewer")) {
                continue;
            }
            String field = entry.getKey().split("\\.")[1];
            switch (field) {
                case("subscribed"): {
                    viewerSubscribed = parseViewerSubscribed(entry.getValue());
                    break;
                }
                case("age"): {
                    viewerAge = parseViewerAge(entry.getValue());
                    break;
                }
                case("gender"): {
                    viewerGender = parseViewerGender(entry.getValue());
                    break;
                }
                case("interests"): {
                    viewerInterests = parseViewerInterests(entry.getValue());
                    break;
                }
            }
        }

        return new Viewer(viewerAge, viewerGender, viewerInterests, viewerSubscribed);
    }

    private static Map<String, String> toFieldMap(String[] dataList) {
        String separator = "=";
        Map<String, String> result = new HashMap<String, String>();
        for (String dataLine : dataList) {
            if (dataLine == null) {
                continue;
            }

            String[] fieldAndValue = dataLine.split(separator, 2);
            if (fieldAndValue.length < 2) {
                System.err.println("Malformed token in payload: " + dataLine);
                continue;
            }

            String field = fieldAndValue[0].trim();
            String value = fieldAndValue[1].trim();

            if (field.isEmpty()) {
                System.err.println("Missing field key in payload token: " + dataLine);
                continue;
            }

            result.put(field, value);
        }
        return result;
    }

    private static Category parseVideoCategory(String rawCategory) {
        Category result = Category.getByName(rawCategory);

        if (result == null) {
            System.err.println("Error parsing videoCategory");
        }

        return result;
    }

    private static ViewerSubscribed parseViewerSubscribed(String rawSubscribed) {
        ViewerSubscribed result = ViewerSubscribed.valueOf(rawSubscribed);

        if (result == null) {
            System.err.println("Error parsing viewerSubscribed");
        }

        return result;
    }
    private static ViewerAge parseViewerAge(String rawAge) {
        ViewerAge result = ViewerAge.fromProtocolValue(rawAge);

        if (result == null) {
            System.err.println("Error parsing viewerAge");
        }

        return result;
    }
    private static ViewerGender parseViewerGender(String rawGender) {
        ViewerGender result = ViewerGender.valueOf(rawGender);

        if (result == null) {
            System.err.println("Error parsing viewerGender");
        }

        return result;

    }
    private static ViewerInterests parseViewerInterests(String rawInterests) {
        Category[] interests = new Category[3];

        String[] interestsString = rawInterests.split(";");

        int currIndexToAdd = 0;
        for (String interestString : interestsString) {
            Category interest = Category.getByName(interestString);
            interests[currIndexToAdd] = interest;
            currIndexToAdd++;
        }

        if (interests[0] == null) {
            System.err.println("Error parsing viewerInterests");
        }
        return new ViewerInterests(interests);
    }

    public static BidResult parseBidResult(String data) {
        data = data.trim();
        if (data.equals("L")) {
            return new BidResult(false, 0);
        }
        String[] wonData = data.split(" ");
        if (wonData.length < 2) {
            System.err.println("Malformed bid result payload: " + data);
            return new BidResult(false, 0);
        }

        try {
            return new BidResult(true, Integer.parseInt(wonData[1]));
        } catch (NumberFormatException e) {
            System.err.println("Malformed win cost in bid result payload: " + data);
            return new BidResult(false, 0);
        }
    }

    public static Summary parseSummary(String data) {
        String[] splitData = data.split(" ");
        if (splitData.length < 3) {
            System.err.println("Malformed summary payload: " + data);
            return new Summary(0, 0);
        }

        try {
            return new Summary(Integer.parseInt(splitData[1]), Integer.parseInt(splitData[2]));
        } catch (NumberFormatException e) {
            System.err.println("Malformed numeric value in summary payload: " + data);
            return new Summary(0, 0);
        }
    }
}
