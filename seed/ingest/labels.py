"""Allowed labels — keep in sync with InterestCatalog / Article model in the Android app."""

SCOPES = ("local", "national", "international")

BIASES = ("Neutral", "Democrat-leaning", "Republican-leaning")

INTERESTS = (
    "Local",
    "National",
    "International",
    "Politics",
    "Climate Change",
    "AI",
    "Technology",
    "Wars & Conflicts",
    "Economy",
    "Health",
    "Abortion",
    "Immigration",
    "Education",
    "Science",
)

# Fallback when the classifier is unsure; keyed by lowercase source name substrings.
SOURCE_BIAS: dict[str, str] = {
    "associated press": "Neutral",
    "ap news": "Neutral",
    "reuters": "Neutral",
    "bbc": "Neutral",
    "npr": "Neutral",
    "pbs": "Neutral",
    "cnn": "Democrat-leaning",
    "msnbc": "Democrat-leaning",
    "the guardian": "Democrat-leaning",
    "nytimes": "Democrat-leaning",
    "new york times": "Democrat-leaning",
    "washington post": "Democrat-leaning",
    "fox news": "Republican-leaning",
    "foxnews": "Republican-leaning",
    "new york post": "Republican-leaning",
    "washington examiner": "Republican-leaning",
    "daily wire": "Republican-leaning",
    "breitbart": "Republican-leaning",
}


def bias_for_source(source: str) -> str:
    name = (source or "").lower()
    for needle, bias in SOURCE_BIAS.items():
        if needle in name:
            return bias
    return "Neutral"
