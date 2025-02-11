{
  description = "riscv test flake";

  # Blocked by chiseltest until 6.0.0
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/f4f8e7c13d3e3b33e9a43f1e1ff97d1697ec6240";
  #  inputs.nixpkgs.url = "nixpkgs/nixpkgs-unstable";
  inputs.nixpkgs-pineapplehunter2.url = "github:pineapplehunter/nixpkgs/espresso";
  inputs.flake-utils.url = "github:numtide/flake-utils";
  inputs.nix-filter.url = "github:numtide/nix-filter";
  inputs.sbt-derivation = {
    url = "github:zaninime/sbt-derivation";
    inputs.nixpkgs.follows = "nixpkgs";
    inputs.flake-utils.follows = "flake-utils";
  };
  inputs.riscv-test-src = {
    url = "https://github.com/pineapplehunter/riscv-tests";
    type = "git";
    submodules = true;
    flake = false;
  };

  outputs = { self, nixpkgs, ... }@inputs:
    inputs.flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        overlays = final: prev: {
          verilator_4 = final.callPackage ./nix/verilator_4.nix { };
          espresso = (import inputs.nixpkgs-pineapplehunter2 { inherit system; config.allowUnfree = true; }).espresso;
          b4smtGen = final.callPackage ./nix { riscv-programs = self.packages.${system}.default; };
          b4smt = final.b4smtGen { hash = "sha256-dm8qlhY87+9tKoX9TWACi+yyzPbSFEnQTGEdJmQl4LE="; };
        };
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            inputs.nix-filter.overlays.default
            inputs.sbt-derivation.overlays.default
            overlays
          ];
        };

      in
      {
        packages = rec {
          riscv-tests = pkgs.callPackage ./riscv-tests-files { inherit (inputs) riscv-test-src; };
          riscv-sample-programs = pkgs.callPackage ./riscv-sample-programs/sample-programs.nix { };
          processor = pkgs.b4smt;
          default = pkgs.linkFarm "processor test programs" [
            { name = "riscv-tests"; path = riscv-tests; }
            { name = "riscv-sample-programs"; path = riscv-sample-programs; }
          ];
          slowChecks = pkgs.b4smt.sbtTest ''sbt "testOnly * -- -n org.scalatest.tags.Slow"'';
          verilator = pkgs.verilator_4;
        };

        checks =
          {
            quick = pkgs.b4smt.sbtTest ''sbt "testOnly * -- -l org.scalatest.tags.Slow"'';
            #            programs = sbtTest ''sbt "testOnly *ProgramTest*"'';
          };

        formatter = pkgs.nixpkgs-fmt;

        devShells.default = pkgs.mkShell {
          name = "processor-shell";
          buildInputs = with pkgs;[
            circt
            rustfilt
            pkgsCross.riscv64.stdenv.cc
            sbt
            jdk
            verilog
            verilator_4
            zlib
            yosys
            graphviz
            xdot
            espresso
            z3
            symbiyosys
            yices
          ];
        };
      });
}
