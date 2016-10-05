# 自動テスト実施について

データメンテナンスの自動テストは、データ作成部分で、APIServer、SDKに依存している。

その為、自動テストの実施には以下の手順を踏む必要がある。

## 1. SDKのjarを作成し、`dsmoq/server/maintenance/lib`に配置する

```
$ cd dsmoq/sdk
$ sbt assembly
$ cp target/scala-2.11/dsmoq-sdk_2.11-1.0.0.jar ../server/maintenance/lib/
```

## 2. テスト用のapplication.confを作成する。

`dsmoq/server/maintenance/src/test/resources/application.conf.sample`をコピーして、`application.conf`を作成する。

以下、設定時の注意点。

* `search_limit`は設定値を変更しないこと。
    * 現在の設定値に依存しているテストケースがある
* `image_dir`、`file_dir`、`app_dir`はAPIServerの設定(`dsmoq/server/apiServer/src/main/resources/application.conf`)と値を合わせること。
    * ディレクトリを正しく参照できない場合、通らないテストケースがある

## 3. APIServerを起動する

起動方法は、container:start、dsmoq.jarからの起動のどちらでも構わない。

dsmoq.jarからの起動の場合は、dsmoq.jar作成時に以下の設定に注意する。

### `dsmoq/server/common/src/main/resources/application.conf`の設定

DB接続先がデータメンテナンスと同じになるようにすること。

### `dsmoq/server/apiServer/src/main/resources/application.conf`の設定

`apiserver.port`は8080であること。

## 補足
データメンテナンスをassemblyする場合、`dsmoq/server/maintenance/lib`にSDKのjarを配置したままだと、jarに含まれるファイルが衝突してエラーになる。

そのため、assembly時はSDKのjarを取り除くこと。
