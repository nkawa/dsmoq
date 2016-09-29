# 自動テスト実施について

データメンテナンスの自動テストは、データ作成部分で、APIServer、SDKに依存している。

その為、自動テストの実施には以下の手順を踏む必要がある。

## 1. SDKのjarを作成し、`dsmoq/server/maintenance/lib`に配置する

```
$ cd dsmoq/sdk
$ sbt assembly
$ cp target/scala-2.11/dsmoq-sdk_2.11-1.0.0.jar ../server/maintenance/lib/
```

## 2. APIServerを起動する

起動方法は、container:start、dsmoq.jarからの起動のどちらでも構わない。

dsmoq.jarからの起動の場合は、以下の設定に注意する。

### `dsmoq/server/common/src/main/resources/application.conf`の設定

DB接続先がデータメンテナンスと同じになるようにすること。

### `dsmoq/server/apiServer/src/main/resources/application.conf`の設定

`apiserver.port`が`maintenance.dsmoq_url_root`と矛盾しないようにすること。

`apiserver.image_dir`、`apiserver.file_dir`、`apiserver.app_dir`の値を合わせること。
