# Bisq API Reference Doc Generator

[Produces Bisq API Reference Doc.](https://bisq-network.github.io/slate)

## What is bisq-api-reference?

This application script consumes Bisq .proto files (`pb.proto` and `grpc.proto`), and produces
`index.html.md` -- a [Slate](https://github.com/slatedocs/slate) compatible Markdown file.

_Note:  For Python proto generation to work properly, the .proto files must be copied to the project's proto 
directory from the remote or local GitHub repo.  They are not versioned in this project repo._

The Markdown file is then manually deployed to a developer's or bisq-network's fork of the
[Slate repository](https://github.com/slatedocs/slate), where the slate fork transforms it to a single html file
describing Bisq gRPC API services, with example code.

## Usage

1. [Fork Slate](https://github.com/slatedocs/slate)


2. Run `BisqApiDocMain`:

```asciidoc
BisqApiDocMain --protosIn=<path-to-bisq-protub-files>/proto 
            \ --markdownOut=<path-to-slate-home>/source 
            \ --failOnMissingDocumentation=false
```

3. Commit changes in your local slate/source file (`index.html.md`) _to your forked slate repo_.

```asciidoc
git commit -a -m "Update index.html.md"
```

4. Run slate's `deploy.sh` script


5. After a few minutes, see changes on in your forked slate's github pages site. For example,</br>
   if your `index.html.md` file was deployed to bisq-network, the URL will be
   https://bisq-network.github.io/slate.


## Credits

Credit to [Juntao Han](https://github.com/mstao) for making his [markdown4j](https://github.com/mstao/markdown4j)
API available for use in this project.  His source code is included in this project, modified in ways to make
generated markdown blocks compatible with Slate/GitHub style markdown.

Lombok annotations are also replaced by the implementations they would generate if that had worked in my
gradle development setup.



