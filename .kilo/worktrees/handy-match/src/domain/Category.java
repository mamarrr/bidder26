package domain;
public enum Category {
    MUSIC("Music"),
    SPORTS("Sports"),
    KIDS("Kids"),
    DIY("DIY"),
    VIDEOGAMES("Video Games"),
    ASMR("ASMR"),
    BEAUTY("Beauty"),
    COOKING("Cooking"),
    FINANCE("Finance");

    private final String name;

    Category(String s) {
        this.name = s;
    }

    public String getName() {
        return this.name;
    }

    public static Category getByName(String name) {
        for (Category category : Category.values()) {
            if (normalizeName(category.name).equalsIgnoreCase(normalizeName(name))) {
                return category;
            }
        }

        System.err.println("Category with name " + name + " does not exist.");
        return null;
    }

    private static String normalizeName(String name) {
        return name.replace(" ", "");

    }
}
