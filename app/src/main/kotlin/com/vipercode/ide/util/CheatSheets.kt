package com.vipercode.ide.util

/**
 * v0.0.9 — SUPER UPDATE: built-in offline cheat sheets for the most
 * common developer tools (Git, Vim, Regex, HTTP status codes, common
 * keyboard shortcuts, etc.).
 *
 * Each entry is a list of (title, body) pairs rendered by the new
 * CheatSheetScreen. Adding a new sheet is a one-step change: append
 * an entry to [CheatSheets.all].
 *
 * The data is intentionally a `List<Pair<String, String>>` (vs a
 * fancy Markdown file) so it can be rendered without a Markdown
 * parser dependency — the body is plain text with simple `\n`
 * separators.
 */
object CheatSheets {

    data class Sheet(
        val id: String,
        val title: String,
        val description: String,
        val sections: List<Pair<String, String>>,
    )

    val Git = Sheet(
        id = "git",
        title = "Git cheat sheet",
        description = "The 80% of Git you use 80% of the time",
        sections = listOf(
            "Setup" to "git config --global user.name \"Name\"\ngit config --global user.email \"email@example.com\"\ngit config --global init.defaultBranch main",
            "Start a repo" to "git init\ngit clone <url>\ngit clone --depth=1 <url>  # shallow",
            "Stage + commit" to "git add .\ngit add -p           # interactive\ngit commit -m \"msg\"\ngit commit --amend   # fix last commit",
            "Branch" to "git branch <name>\ngit checkout <name>\ngit checkout -b <name>  # create+switch\ngit switch <name>\ngit switch -c <name>",
            "Merge + rebase" to "git merge <branch>\ngit rebase main\ngit rebase -i HEAD~3   # interactive",
            "Remote" to "git remote add origin <url>\ngit fetch\ngit pull\ngit push -u origin main",
            "Inspect" to "git status\ngit log --oneline --graph\ngit diff\ngit diff --staged\ngit show <hash>",
            "Undo" to "git restore <file>\ngit restore --staged <file>\ngit reset --hard HEAD\ngit clean -fd",
            "Stash" to "git stash\ngit stash pop\ngit stash list\ngit stash apply stash@{1}",
            "Tag" to "git tag v1.0\ngit tag -a v1.0 -m \"msg\"\ngit push origin v1.0",
        ),
    )

    val Vim = Sheet(
        id = "vim",
        title = "Vim cheat sheet",
        description = "Modal editing survival kit",
        sections = listOf(
            "Modes" to "i / a / o   enter insert mode\nEsc          back to normal\nv            visual\nV            visual line\nCtrl+v       block",
            "Save + quit" to ":w            write\n:q            quit\n:wq / :x      write+quit\n:q!           force quit\nZZ / ZQ       write+quit / quit",
            "Movement" to "h j k l       left/down/up/right\nw / b         next/previous word\n0 / ^ / \$    first/first-non-ws/last col\ngg / G        first/last line\nCtrl+d / u    half-page down/up",
            "Edit" to "dd            delete line\ncc            change line\nyy            yank (copy) line\np / P         paste after/before\nu / Ctrl+r    undo/redo",
            "Search" to "/pattern      search forward\n?pattern      search backward\nn / N         next/previous match\n:%s/old/new/g replace all",
            "Windows" to ":split / :vsplit   horizontal/vertical split\nCtrl+w h/j/k/l     move between splits\n:q                 close split",
            "Buffers" to ":ls           list buffers\n:b N         switch to buffer N\n:bd          delete buffer\n:bn / :bp    next/previous",
        ),
    )

    val Regex = Sheet(
        id = "regex",
        title = "Regex cheat sheet",
        description = "Regular expression quick reference (PCRE flavour)",
        sections = listOf(
            "Anchors" to "^     start of line\n\$     end of line\n\\b    word boundary\n\\B    non-word boundary\n\\A    start of string\n\\z    end of string",
            "Character classes" to ".     any char\n\\d \\D  digit / non-digit\n\\w \\W  word / non-word\n\\s \\S  whitespace / non-ws\n[abc]  any of a,b,c\n[^abc] none of a,b,c\n[a-z]  range",
            "Quantifiers" to "*       0 or more\n+       1 or more\n?       0 or 1\n{n}     exactly n\n{n,}    n or more\n{n,m}   between n and m",
            "Groups" to "(abc)   capture group\n(?:abc) non-capture group\n(?=abc) positive lookahead\n(?!abc) negative lookahead\n(?P<name>abc) named group",
            "Lookbehind" to "(?<=abc) positive lookbehind\n(?<!abc) negative lookbehind",
            "Common patterns" to "Email: [\\w.+-]+@[\\w-]+\\.[\\w.]+\nURL: https?://[\\w./?=&%-]+\nIPv4: \\d{1,3}(\\.\\d{1,3}){3}\nHex: #[0-9a-fA-F]{6}\nISO date: \\d{4}-\\d{2}-\\d{2}",
        ),
    )

    val Http = Sheet(
        id = "http",
        title = "HTTP status codes",
        description = "All 5 classes + the common ones you'll actually see",
        sections = listOf(
            "1xx Informational" to "100 Continue\n101 Switching Protocols\n102 Processing",
            "2xx Success" to "200 OK\n201 Created\n202 Accepted\n204 No Content\n206 Partial Content",
            "3xx Redirection" to "301 Moved Permanently\n302 Found (Temporary)\n304 Not Modified\n307 Temporary Redirect (method preserved)\n308 Permanent Redirect",
            "4xx Client error" to "400 Bad Request\n401 Unauthorized\n403 Forbidden\n404 Not Found\n405 Method Not Allowed\n409 Conflict\n410 Gone\n418 I'm a teapot\n422 Unprocessable Entity\n429 Too Many Requests",
            "5xx Server error" to "500 Internal Server Error\n501 Not Implemented\n502 Bad Gateway\n503 Service Unavailable\n504 Gateway Timeout\n511 Network Authentication Required",
            "Methods" to "GET       fetch\nPOST      create\nPUT       replace\nPATCH     partial update\nDELETE    remove\nHEAD      headers only\nOPTIONS   CORS preflight",
        ),
    )

    val Markdown = Sheet(
        id = "markdown",
        title = "Markdown cheat sheet",
        description = "GFM-flavoured Markdown reference",
        sections = listOf(
            "Headers" to "# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6",
            "Emphasis" to "*italic* or _italic_\n**bold** or __bold__\n***bold+italic***\n~~strikethrough~~\n`inline code`",
            "Lists" to "- unordered\n  - nested\n1. ordered\n2. item\n- [ ] task\n- [x] done",
            "Links + images" to "[text](https://url)\n[text](https://url \"title\")\n[ref][1]\n[1]: https://url\n![alt](img.png)\n![alt](img.png \"title\")",
            "Code blocks" to "```\nplain code block\n```\n```kotlin\nfun main() { println(\"hi\") }\n```",
            "Blockquote" to "> single line\n>\n> multi\n> paragraph",
            "Table" to "| Col1 | Col2 |\n|------|------|\n| a    | b    |\n| c    | d    |",
            "HR" to "---\nor\n***\nor\n___",
        ),
    )

    val Docker = Sheet(
        id = "docker",
        title = "Docker cheat sheet",
        description = "Common Docker + docker-compose commands",
        sections = listOf(
            "Images" to "docker build -t name:tag .\ndocker images\ndocker rmi <id>\ndocker tag <src> <dst>\ndocker save -o file.tar name\ndocker load -i file.tar",
            "Containers" to "docker run --name c1 -p 8080:80 -d name:tag\ndocker ps\n docker ps -a\ndocker stop <id>\ndocker rm <id>\ndocker logs -f <id>",
            "Exec" to "docker exec -it <id> sh\ndocker exec -it <id> bash\ndocker cp <id>:/path /local\ndocker cp /local <id>:/path",
            "Compose" to "docker-compose up -d\ndocker-compose down\ndocker-compose logs -f\ndocker-compose build\ndocker-compose exec web sh\ndocker-compose ps",
            "Volumes" to "docker volume ls\ndocker volume create v1\ndocker volume rm v1\ndocker volume inspect v1",
            "Networks" to "docker network ls\ndocker network create n1\ndocker network rm n1",
            "Cleanup" to "docker system prune\ndocker system prune -a --volumes\ndocker image prune -a\ndocker container prune\ndocker volume prune",
        ),
    )

    val Kotlin = Sheet(
        id = "kotlin",
        title = "Kotlin cheat sheet",
        description = "Idiomatic Kotlin quick reference",
        sections = listOf(
            "Variables" to "val x = 1        // immutable\nvar y = 2        // mutable\nlateinit var z: String\nconst val PI = 3.14",
            "Null safety" to "var s: String? = null\ns?.length          // safe call\ns ?: \"default\"     // elvis\ns!!                // assert non-null\nval len = s?.length ?: 0",
            "Functions" to "fun add(a: Int, b: Int): Int = a + b\nfun print(s: String) { println(s) }\nfun String.shout() = uppercase()  // ext\nfun build(block: Builder.() -> Unit) = …  // DSL",
            "When" to "when (x) {\n    1 -> \"one\"\n    2, 3 -> \"few\"\n    in 4..10 -> \"many\"\n    else -> \"lots\"\n}",
            "Collections" to "listOf(1,2,3)\nmapOf(\"a\" to 1)\nsetOf(1,2,3)\nnums.map { it*2 }.filter { it > 2 }\nnums.sum()\nnums.groupBy { it % 2 }\nnums.associate { it to it*2 }",
            "Coroutines" to "suspend fun fetch(): String = …\nrunBlocking { fetch() }\nlaunch { }          // fire-and-forget\nasync { }           // returns Deferred\nval x = awaitAll(d1, d2)\nwithContext(Dispatchers.IO) { }",
            "Scope functions" to "let:   it / returns last expr\nrun:   this / returns last expr\nwith:  this arg / returns last\napply: this / returns object\nalso:  it / returns object",
        ),
    )

    val Python = Sheet(
        id = "python",
        title = "Python cheat sheet",
        description = "Python 3.10+ essentials",
        sections = listOf(
            "Types" to "x: int = 1\ny: str = \"hi\"\nz: list[int] = [1,2,3]\nm: dict[str, int] = {\"a\": 1}\nt: tuple[int, str] = (1, \"a\")\ne: Literal[\"a\",\"b\"] = \"a\"",
            "Comprehensions" to "[x*2 for x in range(10)]\n[x for x in xs if x > 0]\n{k: v for k, v in items}\n{x for x in xs}    # set\n(x for x in xs)    # generator",
            "Match (3.10+)" to "match x:\n    case 1: print(\"one\")\n    case [a, b]: print(f\"{a},{b}\")\n    case {\"key\": v}: print(v)\n    case _: print(\"other\")",
            "Async" to "async def fetch():\n    await asyncio.sleep(1)\n    return \"ok\"\n\nawait asyncio.gather(fetch(), fetch())",
            "Files" to "with open(\"f.txt\") as f:\n    text = f.read()\n    for line in f: ...\n\nimport pathlib\npath = pathlib.Path(\"f.txt\")\npath.read_text()",
            "Common" to "range(10)\nenumerate(xs)\nzip(a, b)\nsorted(xs, key=lambda x: -x)\nmap(f, xs)\nfilter(pred, xs)\nsum(xs), min(xs), max(xs)",
        ),
    )

    val Css = Sheet(
        id = "css",
        title = "CSS cheat sheet",
        description = "Modern CSS quick reference",
        sections = listOf(
            "Selectors" to ".class\n#id\ndiv > p\ndiv + p\ndiv ~ p\na[href^=\"https\"]\np::before\n::placeholder",
            "Flexbox" to "display: flex\nflex-direction: row | column\njustify-content: center\nalign-items: center\ngap: 16px\nflex: 1\nflex-wrap: wrap",
            "Grid" to "display: grid\ngrid-template-columns: repeat(3, 1fr)\ngrid-template-rows: auto 1fr auto\ngap: 16px\ngrid-area: 1 / 1 / 2 / 3",
            "Position" to "position: static | relative | absolute | fixed | sticky\ntop: 0; right: 0; bottom: 0; left: 0;\nz-index: 10",
            "Variables" to ":root {\n  --primary: #1976d2;\n  --radius: 8px;\n}\n.btn { background: var(--primary); border-radius: var(--radius); }",
            "Media queries" to "@media (max-width: 768px) { … }\n@media (prefers-color-scheme: dark) { … }\n@media (hover: hover) { … }",
            "Animations" to "transition: all 0.2s ease;\nanimation: spin 1s linear infinite;\n@keyframes spin { to { transform: rotate(360deg); } }",
        ),
    )

    val Shell = Sheet(
        id = "shell",
        title = "Shell cheat sheet",
        description = "Bash + POSIX shell essentials",
        sections = listOf(
            "Variables" to "FOO=\"hello\"\necho $FOO\nread -p \"Name: \" name\nlocal var=1   # function scope\n${var:-default}\n${var:=default}\n${#var}        # length\n${var/old/new}",
            "Conditionals" to "if [ -f file ]; then …; fi\nif [[ \"$x\" == \"hi\" ]]; then …; fi\ncase $x in\n  a) echo a;;\n  b|c) echo bc;;\n  *) echo default;;\nesac",
            "Loops" to "for i in 1 2 3; do echo $i; done\nfor f in *.txt; do mv \"$f\" \"${f%.txt}.md\"; done\nwhile read line; do echo $line; done < file\nuntil false; do …; done",
            "Redirection" to "cmd > file           # stdout\ncmd >> file          # append\ncmd 2> file          # stderr\ncmd > out 2> err\ncmd &> file          # both\ncmd | tee file       # tee\ncmd < file           # stdin",
            "One-liners" to "find . -name '*.kt' -type f\ngrep -rn 'TODO' .\nsed -i 's/old/new/g' file\nawk '{print $2}' file\nxargs -I{} cmd {}\ncut -d, -f2 file.csv\nsort | uniq -c | sort -rn",
            "Subshells" to "result=$(cmd)\n( cd /tmp && do_stuff )\n{ cmd1; cmd2; } > out\ntrap 'cleanup' EXIT",
        ),
    )

    /** All sheets shipped with ViperCode v0.0.9. */
    val all: List<Sheet> = listOf(
        Git, Vim, Regex, Http, Markdown, Docker, Kotlin, Python, Css, Shell,
    )

    /** Lookup by id; returns null if not found. */
    fun byId(id: String): Sheet? = all.firstOrNull { it.id == id }
}
