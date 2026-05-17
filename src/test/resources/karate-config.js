function fn() {
  return {
    baseUrl: karate.properties['karate.baseUrl'],
    support: Java.type('kr.ac.koreatech.indoor.vps.karate.support.AcceptanceKarateSupport')
  };
}
