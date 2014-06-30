package org.gradle.api.plugins.changelog

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GitChangelogTask extends DefaultTask {
    def service
    @TaskAction
    def changelog() {
        service = new GitChangelogService(project)

        def commits
        def opts = [:]

        opts.file           = project.changelog.file ? project.changelog.file : "CHANGELOG.md"
        opts.version        = project.changelog.versionNum ? project.changelog.versionNum : ""
        opts.versionText    = project.changelog.versionText ? "\"${project.changelog.versionText}\"" : ""
        opts.appName        = project.changelog.appName ? project.changelog.appName : ""
        opts.grep           = project.changelog.grep ? project.changelog.grep : "^fix|^feat|^fix|^perf|BREAKING"
        opts.repoUrl        = project.changelog.repoUrl ? project.changelog.repoUrl : ""
        opts.from           = project.changelog.from ? project.changelog.from : service.getPreviousTag()
        opts.to             = project.changelog.to ? project.changelog.to : "HEAD"

        commits = opts.from ? service.readGitLog(opts.grep, opts.from, opts.to) : service.readGitLog(opts.grep)

        println "Parsed ${commits.size()} commits"
        println "Generating changelog to ${opts.file} ( ${opts.version} )"
        service.writeChangelog(new RandomAccessFile(new File("$opts.file"), "rw"), commits, opts)
    }

}