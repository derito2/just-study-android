package com.dero.juststudykorean.korean.coach

// Port of backend/app/agent/categories.py and meanings.THEME_NAMES: common themes with a fixed list of English words
// to tell which meanings of your notes belong ("colors": "Red" yes, "To be warm" no). No model, no embeddings.

private fun words(s: String) = s.trim().split(Regex("\\s+")).toSet()

val CATEGORIES: Map<String, Set<String>> = linkedMapOf(
    "colors" to words(
        "color colour red black white blue green yellow orange purple pink brown gray grey navy beige gold silver violet ivory sky-blue light-blue dark-blue khaki",
    ),
    "family" to words(
        "family mother mom mommy father dad daddy parent parents son daughter brother sister sibling siblings child children kid kids baby " +
            "grandmother grandma grandfather grandpa grandparents grandchild grandson granddaughter aunt uncle cousin husband wife spouse " +
            "nephew niece relative relatives in-law sister-in-law brother-in-law older-brother older-sister younger-brother younger-sister",
    ),
    "animals" to words(
        "animal animals pet dog puppy cat kitten bird fish horse cow pig sheep goat chicken duck rabbit mouse rat tiger lion bear elephant " +
            "monkey snake frog turtle fox wolf deer giraffe zebra whale dolphin shark insect bug butterfly bee ant spider",
    ),
    "body" to words(
        "body head face hair eye eyes ear ears nose mouth lip lips tooth teeth tongue neck shoulder shoulders arm arms hand hands finger " +
            "fingers leg legs knee knees foot feet toe toes back chest stomach belly heart skin bone blood waist hip",
    ),
    "food" to words(
        "food meal rice bread noodle noodles soup meat beef pork chicken fish shrimp egg eggs vegetable vegetables fruit apple banana " +
            "orange grape strawberry watermelon potato tomato onion garlic cabbage kimchi tofu cheese milk butter sugar salt pepper cake " +
            "cookie snack dessert breakfast lunch dinner sandwich pizza hamburger salad",
    ),
    "drinks" to words("drink drinks water coffee tea milk juice beer wine soju soda cola alcohol"),
    "weather" to words(
        "weather rain rainy snow snowy wind windy cloud cloudy sun sunny storm typhoon fog foggy temperature hot cold warm cool humid dry " +
            "thunder lightning rainbow season spring summer autumn fall winter umbrella",
    ),
    "clothes" to words(
        "clothes clothing shirt t-shirt pants trousers jeans skirt dress coat jacket sweater hat cap shoe shoes sneakers boots socks sock " +
            "underwear glasses scarf gloves belt uniform suit pajamas",
    ),
    "numbers" to words(
        "number one two three four five six seven eight nine ten eleven twelve twenty thirty hundred thousand million first second third zero half",
    ),
    "days" to words(
        "day days week weekend monday tuesday wednesday thursday friday saturday sunday today tomorrow yesterday month year morning afternoon evening night",
    ),
    "emotions" to words(
        "emotion emotions feeling feelings happy sad angry upset scared afraid fear nervous worried anxious lonely bored tired excited " +
            "surprised glad joy love hate jealous embarrassed proud calm cry laugh smile",
    ),
    "transportation" to words(
        "transportation transport car bus subway train taxi airplane plane bicycle bike motorcycle ship boat truck station airport ticket transfer driver",
    ),
    "jobs" to words(
        "job jobs work doctor nurse teacher student police officer driver cook chef farmer singer actor actress lawyer engineer employee boss company office",
    ),
    "house" to words(
        "house home room bedroom bathroom kitchen living-room door window wall floor roof bed table chair sofa desk closet stairs yard garden toilet shower",
    ),
    "school" to words(
        "school class classroom student teacher homework exam test lesson book notebook pen pencil eraser desk university college library grade subject",
    ),
)

private val ALIASES = mapOf(
    "color" to "colors", "colour" to "colors", "colours" to "colors",
    "family members" to "family", "relatives" to "family",
    "animal" to "animals", "pets" to "animals",
    "body parts" to "body", "parts of the body" to "body", "my body" to "body",
    "food and drinks" to "food", "foods" to "food",
    "drink" to "drinks", "beverages" to "drinks",
    "clothing" to "clothes", "outfits" to "clothes",
    "number" to "numbers", "counting" to "numbers",
    "day" to "days", "days of the week" to "days", "time" to "days",
    "emotion" to "emotions", "feelings" to "emotions", "feeling" to "emotions",
    "transport" to "transportation", "vehicles" to "transportation",
    "job" to "jobs", "work" to "jobs", "professions" to "jobs",
    "home" to "house", "houses" to "house", "furniture" to "house", "houses and furniture" to "house",
    "school stuff" to "school", "class" to "school",
)

/** The fixed category for a theme, or null if it is not a common one. */
fun categoryOf(theme: String): String? {
    val key = theme.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    return if (key in CATEGORIES) key else ALIASES[key]
}

private val MEANING_CUT = Regex("[,;/(]")
private val TO_BE = Regex("^to (be )?")
private val MEANING_WORDS = Regex("[a-z]+(?:-[a-z]+)?")

/** Does the meaning (of your notes) name something of the category? "Red" is a color; "To be warm" is not. */
fun belongs(category: String, meaning: String): Boolean {
    val first = meaning.split(MEANING_CUT)[0].lowercase().trim().replaceFirst(TO_BE, "")
    val words = MEANING_WORDS.findAll(first).map { it.value }.toList()
    val tokens = words + words.joinToString("-") + words.filter { it.endsWith("s") && it.length > 3 }.map { it.dropLast(1) }
    return tokens.any { it in CATEGORIES.getValue(category) }
}

private val ANCHORS = listOf(
    "family", "food", "travel", "sports", "weather", "school", "work", "shopping", "body", "health", "emotions", "colors", "animals",
    "house", "clothes", "transportation", "time", "numbers", "movement", "hobbies", "nature", "city", "music", "money", "language",
    "technology", "people", "places", "actions", "daily life",
)

/** English theme names (meanings.THEME_NAMES): only to fix typos in a theme, never words of a quiz. */
val THEME_NAMES: List<String> = ANCHORS + listOf(
    "soccer", "baseball", "basketball", "exercise", "movies", "books", "games", "art", "personality", "friends", "love", "job", "office",
    "restaurant", "drinks", "fruit", "vegetables", "meat", "dessert", "coffee", "cooking", "kitchen", "furniture", "bedroom", "bathroom",
    "cleaning", "hospital", "doctor", "medicine", "sleep", "seasons", "holidays", "christmas", "birthday", "wedding", "airport", "hotel",
    "directions", "shapes", "sizes", "countries", "insects", "birds", "fish", "flowers", "trees", "mountains", "sea", "beach", "sky",
    "space", "rain", "snow", "computers", "phone", "internet", "science", "math", "history", "politics", "police", "religion", "subjects",
    "greetings", "verbs", "adjectives",
)
