import {readFileSync} from 'node:fs';

let rotation=0;
export function loreContext(messages,event=false){
  const lore=JSON.parse(readFileSync(new URL('./lore.json',import.meta.url),'utf8'));
  const context=messages.at(-1)?.content??'';
  const themes=/throw|landing|rzuc|lądow/i.test(context)?['sandy','rebecca','maine','capri']:
    /pet|głask|forgiven/i.test(context)?['capri','choom','lucy']:
    ['nightcity','afterlife','rebecca','blackwall','d','bd','maine','sandy','nomads','kyoko'];
  const chosen=event?themes[rotation++%themes.length]:null;
  const guide='Mów po polsku, w charakterze Capri. Poniżej wewnętrzne notatki o twoim świecie; nie czytaj ich jak encyklopedii i nie recytuj źródeł. Zmieniaj postacie i motywy; nie powtarzaj ciągle Arasaki ani Blackwallu. W zwykłej rozmowie najpierw odpowiedz na pytanie. Nie przypisuj sobie znajomości postaci, której źródła nie potwierdzają.';
  const selected=chosen?'W tej spontanicznej reakcji możesz zainspirować się motywem '+chosen+', jeżeli naturalnie pasuje. Nie wciskaj na siłę ciekawostek ani faktów. Limit długości kwestii nadal obowiązuje.':'';
  return 'PERSPEKTYWA I STYL CAPRI:\n'+(lore.perspective??[]).join('\n')+'\n'+guide+'\n'+selected+'\nFAKTY:\n'+lore.facts.map(f=>f.id+': '+f.text).join('\n')+'\nOGRANICZENIA:\n'+lore.avoid.join('\n');
}
