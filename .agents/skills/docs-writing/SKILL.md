---
name: docs-writing
description: Use when creating or updating Markdown (.md) documentation files.
---

# Writing documentation

Be terse and concise. Write as little text as possible while still being clear. But it is important to be clear, to not skip over such as which subject it pertains.
Do not include overly indulgent implementation details, write about the *purpose* of the documentation.
Write for humans who need to skim the contents, not agents who can consume lots of text.
Prefer bullet points, short tables, and short paragraphs.

Do not overly explain, or refer to something that is not required to understand the purpose of the documentation.
The idea is that if a developer wants to understand how something works, they can read the source code.
They are reading the documentation to understand the purpose and overview of the feature, not how it is implemented.

Avoid writing sentences like "Do this and that" instead separate paragraphs to "Do this. Do that."

Better to write longer lines than to have an unreadable document.

Exclude "changelog"-like paragraphs, that explain what was changed by implementing a new feature.
Rather just explain the feature to a potential reader, what they need to know to understand how the feature works.

Extremely strongly prefer code examples over explaining a feature using words.

## Explain reasoning and relevance
Do not assume that things can be logically deduced from "what is expected" and "what is obvious" to you,
instead state the purpose of one item and the relevance to a next item, or for the next item and its relevance to the previous.
You do not need to force this in, but if it improves readability, reasoning and understanding, then do so.
For example, instead of:
```
* Implement expressions
* Implement evaluator
```

It is better to write:
```
* Implement expressions, which can be used to represent X,
* Implement evaluator, which can evaluate above expressions to produce Y.
```
