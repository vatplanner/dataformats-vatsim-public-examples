# Examples and Testing Tools for dataformats-vatsim-public

This repository collects some basic tools used during development of [dataformats-vatsim-public](https://github.com/vatplanner/dataformats-vatsim-public) which may also serve as examples on how to use the library.

## Preparation

The tools are executed by wrapper scripts calling Maven and require `dataformats-vatsim-public` to be available as a Maven artifact. You need to download [dataformats-vatsim-public](https://github.com/vatplanner/dataformats-vatsim-public) first and execute `mvn clean install` on it to build and install that artifact.

## Dump

The dump tool is used during development to mass-import a series of data files and dump the resulting graph structure. It's preferably called from a *NIX or Windows Git Bash environment by `dump.sh`. Windows users may want to try `dump.bat` as well but should be aware of misinterpretation issues when specifying regular expressions on Windows shell command lines. Therefore, using Git Bash is recommended if you are on Windows.

You need one or more data files (originally `vatsim-data.txt`) which can be compressed and added to an archive. Apart from saving disk space and easier handling, using compressed archives can also dramatically speed up file reading (the best format appears to be `.tar.xz`). However, you may also work with plain files if you want.

`-rp` takes the path to start reading files from. Files are always read in alphabetic order, including files read from archives. You can pass a Java regular expression (see `Pattern`) to match file names using `-rf`. The expression is used for filenames on disk as well as in archives, so you will need to enter an expression matching both an archive filename and the names of files to be read from those archives.

Output can be written to a file specified by `-of`, otherwise the dump will be written to STDOUT.

Dumping the complete graph will yield a very large result. `-dm` can be specified to limit output to a specific VATSIM member (certificate) ID. Multiple members can be dumped at once by specifying multiple `-dm` options such as `-dm 12345 -dm 67890`.

`-rr` can be used to display a progress status every n files (e.g. `-rr 100` to report every 100th file).

```
usage: dump|dump.sh
 -dm,--dumpmember <CID>     only dumps the member identified by given
                            VATSIM certificate ID (repeat option for
                            multiple members)
 -h,--help                  displays this help message
 -oa,--append               appends to the specified output file if it
                            already exists
 -of,--outputfile <FILE>    writes the resulting dump to given FILE
                            instead of stdout
 -oo,--overwrite            overwrites the specified output file if it
                            already exists
 -rf,--readfilter <REGEX>   regular expression to apply as positive filter
                            on filenames; if archives are to be read,
                            regex must match both archive names and names
                            of files in archives to be read
 -rp,--readpath <PATH>      path to a single file, an archive file or a
                            directory of either types to read
 -rr,--readreport <COUNT>   logs a status report after reading every COUNT
                            files
```