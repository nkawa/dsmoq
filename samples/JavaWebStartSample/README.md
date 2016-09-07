# Java Web Start Sample

このプロジェクトは、データセットにアプリとして登録するプログラムのサンプルです。

## ビルド方法

### 事前準備

このプロジェクトは SDK プロジェクトに依存しています。
dsmoq/sdk 内で以下のコマンドを実行し、ローカルリポジトリに SDK を登録しておく必要があります。

```
sbt publishLocal
```

### assembled jar ファイルの作成

以下のコマンドを実行すると target/scala-2.11/ 以下に
dsmoq-jws-sample-assembly-1.0.0.jar ファイルが作成されます。

```
sbt assembly
```

### 署名の付与

Java Web Start のアプリケーションとして登録するためには、
作成されたjarファイルに対して署名を付与する必要があります。

署名を付与しないとユーザ実行時にエラーとなります。

#### 署名付与コマンド例

証明書を取得しない場合、以下のコマンドで署名を付与できます。

```
keytool -genkey -keyalg rsa -keystore sample.jks -alias sample
jarsigner -keystore sample.jks dsmoq-jws-sample-assembly-1.0.0.jar sample
```

証明書を取得していないので、アプリ利用者側のJavaセキュリティ設定で
例外サイト・リストにdsmoqサイトを登録する必要があります。

証明書を取得する方法については、
[公開/秘密鍵のペアと信頼できるエンティティからの証明書を管理するためのキーストアを作成する手順](http://docs.oracle.com/javase/jp/8/docs/technotes/tools/unix/keytool.html#keytool_examples)
 を参照ください。
