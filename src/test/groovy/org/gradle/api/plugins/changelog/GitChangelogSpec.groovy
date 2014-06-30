package org.gradle.api.plugins.changelog

import spock.lang.Specification

class GitChangelogSpec extends Specification {
    def "parseRawCommit should be null when invalid string"(){
        expect:
        commit == null

        where:
        service = new GitChangelogService(null)
        commit = service.parseRawCommit("")
    }

    def "should parse a raw commit"(){
        expect:
        commit != null
        commit.type == 'feat'
        commit.hash == '9b1aff905b638aa274a5fc8f88662df446d374bd'
        commit.subject == 'broadcast $destroy event on scope destruction'
        commit.body == 'perf testing shows that in chrome this change adds 5-15% overhead\n' +
                'when destroying 10k nested scopes where each scope has a $destroy listener'
        commit.component == 'scope'

        where:
        service = new GitChangelogService(null)
        msg = '9b1aff905b638aa274a5fc8f88662df446d374bd\n' +
                'feat(scope): broadcast $destroy event on scope destruction\n' +
                'perf testing shows that in chrome this change adds 5-15% overhead\n' +
                'when destroying 10k nested scopes where each scope has a $destroy listener\n'
        commit = service.parseRawCommit(msg)
    }
    def "should parse closed issues"(){
        expect:
        commit.closes != null
        commit.closes[0] == '123'
        commit.closes[1] == '25'

        where:
        service = new GitChangelogService(null)
        msg = '13f31602f396bc269076ab4d389cfd8ca94b20ba\n' +
                'feat(ng-list): Allow custom separator\n' +
                'bla bla bla\n\n' +
                'Closes #123\nCloses #25\n'
        commit = service.parseRawCommit(msg)
    }

    def "should parse breaking changes"(){
        expect:
        commit.breaking == ' first breaking change\nsomething else\nanother line with more info\n'

        where:
        service = new GitChangelogService(null)
        msg = '13f31602f396bc269076ab4d389cfd8ca94b20ba\n' +
                'feat(ng-list): Allow custom separator\n' +
                'bla bla bla\n\n' +
                'BREAKING CHANGE: first breaking change\nsomething else\n' +
                'another line with more info\n'
        commit = service.parseRawCommit(msg)

    }
}
