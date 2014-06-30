package org.gradle.api.plugins.changelog

class GitChangelogService {
    def GIT_LOG_CMD         = 'git log --grep="%s" -E --format=%s %s..%s'
    def GIT_NOTAG_LOG_CMD   = 'git log --grep="%s" -E --format=%s'
    def GIT_TAG_CMD         = 'git describe --tags --abbrev=0'
    def HEADER_TPL          = '<a name="%s">%s</a>\n# %s %s (%s)\n\n'
    def EMPTY_COMPONENT     = '$$'

    static def LinkedHashMap parseRawCommit(String raw){
        if(raw==null || raw.empty) {
            return null
        }
        List<String> lines = raw.split("\n")
        def msg = [:], match

        msg.hash = lines.remove(0)
        msg.subject = lines.remove(0)
        msg.closes = []
        msg.breaks = []

        //closes
        lines.each {line ->
            match = line =~ /(?:Closes|Fixes)\s#(\d+)/
            if(match){
                match[0] - match[0][1]
                msg.closes.push(match[0][1])
            }
        }

        //breaks
        match = raw =~ /BREAKING CHANGE:([\s\S]*)/
        if (match) {
            msg.breaking = match[0][1];
        }

        msg.body = lines.join('\n');
        match = msg.subject =~ /^(.*)\((.*)\):\s(.*)$/

        //parse subject
        if(match.size() == 0){
            match = msg.subject =~ /^(.*):\s(.*)$/
            if(!match){
                println "Incorrect message: ${msg.hash} ${msg.subject}"
                return null;
            }
            msg.type = match[0][1];
            msg.subject = match[0][2];

            return msg;
        }

        msg.type = match[0][1];
        msg.component = match[0][2];
        msg.subject = match[0][3];

        return msg;
    }

    def ArrayList readGitLog(grep, from = null, to = null){
        def cmd
        if(from){
            cmd = String.format(GIT_LOG_CMD, grep, '%H%n%s%n%b%n==END==', from, to).split(" ")
        }else{
            cmd = String.format(GIT_NOTAG_LOG_CMD, grep, '%H%n%s%n%b%n==END==').split(" ")
        }
        def out = new ByteArrayOutputStream()
        println "DEBUG(cmd): ${cmd.join(" ")}"
        def res = project.exec {
            if (System.properties['os.name'].toLowerCase().contains('windows')) {
                commandLine 'cmd', '/c', cmd
            } else {
                commandLine cmd
            }

            standardOutput out
        }
        def arr = out.toString().split("\n==END==\n");

        def commits = []
        for (int i = 0; i < arr.length; i++) {
            def commit = parseRawCommit(arr[i])
            if(commit!=null) {
                commits.add(commit)
            }
        }
        return commits
    }

    def getPreviousTag() {
        def cmd = GIT_TAG_CMD.split(" ")
        def out = new ByteArrayOutputStream()
        def outError = new ByteArrayOutputStream()

        project.exec {
            ignoreExitValue true
            if (System.properties['os.name'].toLowerCase().contains('windows')) {
                commandLine 'cmd', '/c', cmd
            } else {
                commandLine cmd
            }

            standardOutput  out
            errorOutput outError
        }
        if(!outError.toString().replace("\n", "").isEmpty()){
            println "Cannot get the previous tag"
        }
        return out.toString().replace("\n", "")
    }

    def writeChangelog(RandomAccessFile fw, List commits, Map opts) {
        def sections = [
                fix : [:],
                feat: [:],
                breaks: [:],
                style: [:],
                refactor: [:],
                test: [:],
                chore: [:],
                docs: [:]
        ]

        //sections.breaks[EMPTY_COMPONENT] = []

        commits.each {c ->
            def section = sections["${c.type}"]
            def component = c.component ? c.component : EMPTY_COMPONENT

            if (section != null) {
                section[component] = section[component] ? section[component] : []
                section[component].push(c);
            }

            if (c.breaking != null) {
                sections.breaks[component] = sections.breaks[component] ? sections.breaks[component] : []
                sections.breaks[component].push([
                        subject: "due to [${c.hash.substring(0,8)}](${opts.repoUrl}/commits/${c.hash}),\n ${c.breaking}",
                        hash: c.hash,
                        closes: []
                ]);
            }
        }
        def b = new byte [fw.length()]
        fw.read(b)
        fw.seek(0)
        fw.write(String.format(HEADER_TPL, opts.version, opts.appName, opts.version, opts.versionText, currentDate()).bytes)
        printSection(opts, fw, 'Documentation', sections.docs)
        printSection(opts, fw, 'Bug Fixes', sections.fix)
        printSection(opts, fw, 'Features', sections.feat)
        printSection(opts, fw, 'Refactor', sections.refactor, false)
        printSection(opts, fw, 'Style', sections.style, false)
        printSection(opts, fw, 'Test', sections.test, false)
        printSection(opts, fw, 'Chore', sections.chore, false)
        printSection(opts, fw, 'Breaking Changes', sections.breaks, false)
        printSection(opts, fw, 'Docs', sections.docs, false)

        fw.write(b)
        fw.close()
    }

    def printSection(Map opts, RandomAccessFile fw, String title, Map section, boolean printCommitLinks = true) {

        if(section.isEmpty()) return;
        section.sort()

        fw.write(String.format("\n## %s\n\n", title).bytes)

        section.each { c ->
            def prefix = '-'
            def nested = section["${c.key}"].size() > 1

            if (c.key != EMPTY_COMPONENT) {
                if (nested) {
                    fw.write(String.format("- **%s:**\n", c.key).bytes)
                    prefix = '  -'
                } else {
                    prefix = String.format("- **%s:**", c.key)
                }
            }

            section[c.key].each {commit ->
                if (printCommitLinks) {
                    fw.write(String.format("%s %s\n  (%s", prefix, commit.subject, linkToCommit("${commit.hash}", opts)).bytes)

                    if (commit.closes.size()) {
                        fw.write((",\n   " + commit.closes.collect().join(", ")).bytes)
                    }
                    fw.write(")\n".bytes)
                } else {
                    fw.write(String.format("%s %s", prefix, commit.subject).bytes)
                }
            }
        }
        fw.write("\n".bytes)
    }

    static def GString linkToCommit(String hash, Map opts) {
        return "[${hash.substring(0,8)}](${opts.repoUrl}/commits/${hash})"
    }

    static def String currentDate() {
        def c = new GregorianCalendar()
        return String.format('%tY/%<tm/%<td', c)
    }

}
