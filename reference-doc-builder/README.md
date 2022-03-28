# Bisq API Reference Doc Builder

The application script in this project consumes commented Bisq .proto
files [pb.proto](https://github.com/bisq-network/bisq/blob/master/proto/src/main/proto/pb.proto),
and [grpc.proto](https://github.com/bisq-network/bisq/blob/master/proto/src/main/proto/grpc.proto), and produces
this [Slate](https://github.com/slatedocs/slate) compatible Markdown
file:  [index.html.md](https://github.com/bisq-network/slate/blob/main/source/index.html.md)

The Markdown file is then manually deployed to the bisq-network's fork of the
[Slate repository](https://github.com/slatedocs/slate), where slate transforms it to a single html file describing Bisq
gRPC API services: the [API Reference](https://bisq-network.github.io/slate).

## Usage

1. [Fork Slate](https://github.com/slatedocs/slate)


2. Run `BisqApiDocMain`:

<pre>
BisqApiDocMain --protosIn=java-examples/src/main/proto \
      --markdownOut=[path-to-slate-fork]/source \
      --failOnMissingDocumentation=false
</pre>

3. Commit changes in your local slate/source file (`index.html.md`) _to your forked slate repo_.

```asciidoc
git commit -a -m "Update index.html.md"
```

4. Run slate's `deploy.sh` script


5. You will see changes on in your forked slate's GitHub pages site after a few minutes. If your `index.html.md` file
   was deployed to the bisq-network fork, the URL will be https://bisq-network.github.io/slate.

## Credits

Credit to [Juntao Han](https://github.com/mstao) for making his [markdown4j](https://github.com/mstao/markdown4j)
API available for use in this project. His source code is included in this project, modified in ways to make generated
markdown blocks compatible with Slate/GitHub style markdown.

Lombok annotations are also replaced by the implementations they would generate if that had worked in my gradle
development setup.



