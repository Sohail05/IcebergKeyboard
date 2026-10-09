# Third-party data

## `app/src/main/assets/words_en.txt`

The 10,000 most common English words (US, no swear words), ranked by frequency, from
[first20hours/google-10000-english](https://github.com/first20hours/google-10000-english)
(`google-10000-english-usa-no-swears.txt`).

Upstream terms: derived from the *Google Web Trillion Word Corpus* (Brants & Franz), distributed by the
Linguistic Data Consortium, with subsets by Peter Norvig and cleanup by Josh Kaufman. *"Educational and
personal/research use of this data is permitted under the LDC license, Norvig's MIT license for his
contributions, and US fair use doctrine. I do not recommend using this data for commercial purposes
without licensing it from the Linguistic Data Consortium."*

**Before any commercial or store release, replace this file** with a word list under a licence that
permits it (see doc/IMPROVEMENTS.md, item B7). The app reads one lowercase word per line, most frequent first.
