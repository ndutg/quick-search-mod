# F-Droid submission templates for Quick Search

Use these as starting points when opening the RFP issue and the `fdroiddata` merge request.

## RFP issue

Title:

```text
Quick Search (com.tk.quicksearch)
```

Body:

```md
### App information

- Name: Quick Search
- Application ID: `com.tk.quicksearch`
- Source code: https://github.com/teja2495/quick-search
- License: MIT
- Build flavor for F-Droid: `fdroid`

### Inclusion approval

I am the upstream author and approve inclusion of Quick Search in F-Droid.

### Notes

- The repository includes Fastlane metadata under `fastlane/metadata/android/en-US/`.
- The F-Droid build flavor excludes Google Play in-app review and in-app update libraries.
- Expected anti-feature: `NonFreeNet` for optional network-backed features the user enables, such as web integrations and AI providers.
```

## fdroiddata merge request

Title:

```text
New app: Quick Search
```

Description:

```md
## Required

- [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy)
- [x] The original app author has been notified (and does not oppose the inclusion)
- [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
- [ ] Builds with `fdroid build` and all pipelines pass
- [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.

## Strongly Recommended

- [x] The upstream app source code repo contains the app metadata _(summary/description/images/changelog/etc)_ in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure
- [x] Releases are tagged and auto update is enabled

## Suggested

- [ ] External repos are added as git submodules instead of srclibs
- [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds)
- [ ] Multiple apks for native code

Closes rfp#<RFP_ISSUE_ID>
Closes fdroiddata#<FDROIDDATA_ISSUE_ID_IF_ANY>
/label ~"New App"
```

## Metadata file reminder

Start from `docs/fdroiddata-example.yml` in this repo and copy it to:

```text
metadata/com.tk.quicksearch.yml
```

in your `fdroiddata` fork.
