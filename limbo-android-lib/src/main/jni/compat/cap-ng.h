/*
 * cap-ng.h - Minimal libcap-ng API stub for Android (bionic has no libcap-ng).
 * Linux capabilities are a no-op here; all functions return harmless values.
 */
#ifndef CAP_NG_H
#define CAP_NG_H

#ifdef __cplusplus
extern "C" {
#endif

/* capability set constants (mirror libcap-ng) */
#define CAPNG_EFFECTIVE   0
#define CAPNG_PERMITTED   1
#define CAPNG_INHERITABLE 2
#define CAPNG_BOUNDING_SET 3

/* action constants */
#define CAPNG_ADD   0
#define CAPNG_DROP  1
#define CAPNG_CLEAR_ALL 0x10
#define CAPNG_CLEAR_BOUNDING 0x20

/* set types */
#define CAPNG_CAPABILITY_SET 0
#define CAPNG_CAPABILITY 1

typedef enum { CAPNG_NO_FLAG = 0, CAPNG_DROP_SUPP_GRP = 1, CAPNG_CLEAR_BOUNDING_SET = 2 } capng_flags_t;
typedef enum { CAPNG_FAIL = -1, CAPNG_OK = 0 } capng_results_t;

/* stubbed functions */
const char *capng_capability_to_name(unsigned int capability);
int capng_getpid(void);
void capng_setpid(int pid);
void capng_clear(int set);
void capng_fill(int set);
int capng_update(int action, int type, unsigned int capability);
int capng_updatev(int action, int type, unsigned int capability, ...);
int capng_apply(int set);
int capng_change_id(int uid, int gid, capng_flags_t flag);
int capng_have_capability(int set, unsigned int capability);
int capng_have_permitted_capability(unsigned int capability);
int capng_lock(void);
char *capng_print_caps_text(int set);
char *capng_print_caps_numeric(int set);
int capng_name_to_capability(const char *name);
capng_results_t capng_get_caps_process(void);
int capng_set_caps_process(void);

#ifdef __cplusplus
}
#endif

#endif /* CAP_NG_H */