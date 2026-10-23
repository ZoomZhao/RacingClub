import { execFileSync } from 'node:child_process';
import { mkdirSync, copyFileSync, readdirSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';
const root=resolve(import.meta.dirname,'..');
const ios=resolve(root,'native/ios');
// Swift sources and bundle resources come from the Xcode project, so both build paths stay identical.
const pbx=readFileSync(resolve(ios,'RacingClub.xcodeproj/project.pbxproj'),'utf8');
const refs=Object.fromEntries([...pbx.matchAll(/^(\w{24}) = \{isa = PBXFileReference;[^\n]*?path = "?([^";]+)"?;/gm)].map(m=>[m[1],m[2]]));
const builds=Object.fromEntries([...pbx.matchAll(/^(\w{24}) = \{isa = PBXBuildFile; fileRef = (\w{24});/gm)].map(m=>[m[1],m[2]]));
const phase=isa=>{const m=pbx.match(new RegExp(`isa = ${isa};[^\\n]*?files = \\(([^)]*)\\)`));if(!m)throw new Error(`Missing ${isa}`);return m[1].split(',').filter(Boolean).map(id=>refs[builds[id.trim()]])};
const sources=phase('PBXSourcesBuildPhase'),resources=phase('PBXResourcesBuildPhase').filter(p=>!p.endsWith('.xcassets'));
const same=(a,b)=>JSON.stringify([...a].sort())===JSON.stringify([...b].sort());
const swiftOnDisk=readdirSync(resolve(ios,'RacingClub')).filter(f=>f.endsWith('.swift')).map(f=>`RacingClub/${f}`);
const resourcesOnDisk=readdirSync(resolve(ios,'RacingClub/Resources')).filter(f=>!f.startsWith('.')).map(f=>`RacingClub/Resources/${f}`);
if(!same(sources,swiftOnDisk))throw new Error(`Xcode Swift sources ${sources} differ from disk ${swiftOnDisk}`);
if(!same(resources,resourcesOnDisk))throw new Error(`Xcode resources ${resources} differ from disk ${resourcesOnDisk}`);
for(const p of [...sources,...resources])if(!existsSync(resolve(ios,p)))throw new Error(`Missing ${p}`);
const env={...process.env,DEVELOPER_DIR:process.env.DEVELOPER_DIR||'/Applications/Xcode.app/Contents/Developer'};
delete env.SDKROOT;
const sdk=execFileSync('/usr/bin/xcrun',['--sdk','iphoneos','--show-sdk-path'],{env,encoding:'utf8'}).trim();
if(!sdk.includes('iPhoneOS'))throw new Error(`Expected iPhoneOS SDK, received: ${sdk}`);
env.SDKROOT=sdk;
const app=resolve(root,'native/ios/build/unsigned/RacingClub.app');
// A rebuilt bundle must not retain resources removed from the manifest.
rmSync(app,{recursive:true,force:true});mkdirSync(app,{recursive:true});
// Mirrors the Release configuration: -O with whole-module optimization.
execFileSync('/usr/bin/xcrun',['swiftc','-parse-as-library','-O','-whole-module-optimization','-sdk',sdk,'-target','arm64-apple-ios17.0',...sources.map(p=>resolve(ios,p)),'-o',resolve(app,'RacingClub')],{env,stdio:'inherit'});
for(const p of resources)copyFileSync(resolve(ios,p),resolve(app,p.split('/').at(-1)));
// The local unsigned build uses PNG icon declarations because this host has no
// iOS simulator runtime for actool. The Xcode target uses AppIcon.appiconset.
for(const [source,name] of [['icon-120.png','AppIcon60x60@2x.png'],['icon-180.png','AppIcon60x60@3x.png'],['icon-76.png','AppIcon76x76~ipad.png'],['icon-152.png','AppIcon76x76@2x~ipad.png'],['icon-167.png','AppIcon83.5x83.5@2x~ipad.png']])copyFileSync(resolve(root,'native/ios/RacingClub/Assets.xcassets/AppIcon.appiconset',source),resolve(app,name));
copyFileSync(resolve(root,'native/ios/Unsigned-Info.plist'),resolve(app,'Info.plist'));
console.log(`Compiled native iOS arm64 app (${sources.length} Swift sources, ${resources.length} resources), unsigned: ${app}`);
