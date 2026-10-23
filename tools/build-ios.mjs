import { execFileSync } from 'node:child_process';
import { mkdirSync, copyFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
const root=resolve(import.meta.dirname,'..');
const env={...process.env,DEVELOPER_DIR:process.env.DEVELOPER_DIR||'/Applications/Xcode.app/Contents/Developer'};
delete env.SDKROOT;
const sdk=execFileSync('/usr/bin/xcrun',['--sdk','iphoneos','--show-sdk-path'],{env,encoding:'utf8'}).trim();
if(!sdk.includes('iPhoneOS'))throw new Error(`Expected iPhoneOS SDK, received: ${sdk}`);
env.SDKROOT=sdk;
const app=resolve(root,'native/ios/build/unsigned/RacingClub.app');mkdirSync(app,{recursive:true});
execFileSync('/usr/bin/xcrun',['swiftc','-parse-as-library','-sdk',sdk,'-target','arm64-apple-ios17.0',resolve(root,'native/ios/RacingClub/GameCore.swift'),resolve(root,'native/ios/RacingClub/RacingClubApp.swift'),'-o',resolve(app,'RacingClub')],{env,stdio:'inherit'});
for(const file of readdirSync(resolve(root,'native/ios/RacingClub/Resources')))copyFileSync(resolve(root,'native/ios/RacingClub/Resources',file),resolve(app,file));
// The local unsigned build uses PNG icon declarations because this host has no
// iOS simulator runtime for actool. The Xcode target uses AppIcon.appiconset.
for(const [source,name] of [['icon-120.png','AppIcon60x60@2x.png'],['icon-180.png','AppIcon60x60@3x.png'],['icon-76.png','AppIcon76x76~ipad.png'],['icon-152.png','AppIcon76x76@2x~ipad.png'],['icon-167.png','AppIcon83.5x83.5@2x~ipad.png']])copyFileSync(resolve(root,'native/ios/RacingClub/Assets.xcassets/AppIcon.appiconset',source),resolve(app,name));
copyFileSync(resolve(root,'native/ios/Unsigned-Info.plist'),resolve(app,'Info.plist'));
console.log(`Compiled native iOS arm64 app, unsigned: ${app}`);
